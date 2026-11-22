package com.shilapi.xcertplay.media

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import com.shilapi.xcertplay.network.GeelyHeadUnit
import com.shilapi.xcertplay.telecom.PhoneAudioRoute

/**
 * Captures one PCM microphone stream and sends it back to the phone as sealed CarPlay RTP.
 *
 * The recorder runs only while the matching audio stream is active, so callers start this after
 * the first downlink audio packet and close it on stream teardown.
 */
internal class MicrophoneUplink(
    private val config: MicrophoneConfig,
    private val onDiagnostic: (String) -> Unit = {},
    private val speakerphoneCall: Boolean = false,
    /** The call's far-end audio; when set, telephony capture runs DiPlay's own echo canceller. */
    private val echoReference: EchoReference? = null,
    private val echoCancellerFactory: (Int, Int, Int) -> CallEchoCanceller? = { frame, rate, tail ->
        SpeexEchoCanceller.create(frame, rate, tail)
    },
    private val opusEncoderFactory: (Int) -> MicrophoneOpusEncoder? = { bitrate ->
        MicrophoneOpusEncoders.create(
            bitrate = bitrate,
            software = { value ->
                SoftwareOpusEncoder(value, onError = { message, error -> Log.w(TAG, message, error) })
            },
        )
    },
    private val noAudioRestartMillis: Long = NO_AUDIO_RESTART_MILLIS,
    /** Read for a report window that recorded no sound; see [silenceDetail]. */
    private val audioManager: AudioManager? = null,
    private val recorderFactory: MicrophoneRecorderFactory = PlatformMicrophoneRecorders,
    /** The pause before each new recorder that [recover] builds; its size is how many it builds per failure. */
    private val rebuildBackoffMillis: List<Long> = REBUILD_BACKOFF_MILLIS,
    private val geelyHeadUnit: () -> Boolean = { GeelyHeadUnit.detected },
    /** Whether the car's phone audio state handles CarPlay calls; see [PhoneAudioRoute.carPlayCallsUseCarAudio]. */
    private val carPhoneState: () -> Boolean = { PhoneAudioRoute.carPlayCallsUseCarAudio() },
    /** The audio mode a call's [MicrophoneCaptureCheck] reads; null when unknown. */
    private val audioMode: () -> Int? = { audioManager?.mode },
    /** The clock of a call's [MicrophoneCaptureCheck], so tests can run a recorder slower than real time. */
    private val captureCheckClock: () -> Long = System::nanoTime,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val stats = MicrophoneCaptureStats(config, report = { message ->
        Log.i(TAG, message)
        onDiagnostic(message)
    })
    @Volatile private var recorder: MicrophoneRecorder? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var opusEncoder: MicrophoneOpusEncoder? = null
    @Volatile private var effects: List<AudioEffect> = emptyList()
    @Volatile private var echoCanceller: CallEchoCanceller? = null
    private var thread: Thread? = null

    // Set by start() before the capture thread starts; after that only the capture thread uses them.
    private var source = MediaRecorder.AudioSource.MIC
    private var channelMask = AndroidAudioFormat.CHANNEL_IN_MONO
    private var bufferSize = 0
    /** Every attempt to get recording back on this stream, whether it worked or not. */
    private var recoveryAttempts = 0
    /** New VOICE_COMMUNICATION recorders in a row that did not record; see [fallBackFromCommunicationSource]. */
    private var failedCommunicationRecorders = 0
    /** This unit refused to build a VOICE_RECOGNITION recorder in [start], so a call never falls back to it. */
    private var recognitionRejected = false
    /** The recorder's rate: the stream's, or the vocoder's after [afterCaptureCheck] rebuilt a call (#1172). */
    private var captureRate = config.sampleRate
    /** [afterCaptureCheck] has spent its rebuild at [VOCODER_RATE]. */
    private var vocoderRateTried = false
    /** [afterCaptureCheck] has spent its change of source. */
    private var captureSourceChanged = false
    /** The call left VOICE_COMMUNICATION because it recorded too slowly or silently; see [leaveRejectedRecognitionSource]. */
    private var communicationCaptureFailed = false

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true

        val channelMask = if (config.channels >= 2) {
            AndroidAudioFormat.CHANNEL_IN_STEREO
        } else {
            AndroidAudioFormat.CHANNEL_IN_MONO
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelMask,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "microphone unavailable rate=${config.sampleRate} channels=${config.channels}")
            stats.failure(MicrophoneFailureStage.MIN_BUFFER, code = minBuffer)
            running.set(false)
            return false
        }

        val source = when {
            config.audioType == TELEPHONY -> telephonyMicrophoneSource(
                speakerphoneCall = speakerphoneCall,
                carPhoneState = carPhoneState(),
                geelyHeadUnit = geelyHeadUnit(),
            )
            // The Geely/Lynk head unit only opens the microphone for the voice-communication
            // source; its voice-recognition source records silence, so Siri never hears.
            config.audioType == "speechrecognition" ->
                if (geelyHeadUnit()) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                }
            else -> MediaRecorder.AudioSource.MIC
        }
        stats.useSource(source)
        val nextEncoder = if (config.codec == AudioCodecKind.OPUS) {
            opusEncoderFactory(config.bitrate ?: 48_000)
        } else {
            null
        }
        if (config.codec == AudioCodecKind.OPUS && nextEncoder == null) {
            Log.w(TAG, "microphone Opus encoder is unavailable")
            stats.failure(MicrophoneFailureStage.ENCODER)
            running.set(false)
            return false
        }
        if (nextEncoder != null) {
            val message = "Microphone: encoder type=${config.audioType} codec=OPUS " +
                "implementation=${nextEncoder.implementation}"
            Log.i(TAG, message)
            runCatching { onDiagnostic(message) }
        }
        this.channelMask = channelMask
        this.bufferSize = maxOf(minBuffer * 2, config.frameBytes * 4)
        this.source = source
        // Some head units throw on VOICE_RECOGNITION at build(); VOICE_COMMUNICATION works there.
        val nextRecorder = createRecorder(source)
            ?: if (source == MediaRecorder.AudioSource.VOICE_RECOGNITION) {
                recognitionRejected = true
                this.source = MediaRecorder.AudioSource.VOICE_COMMUNICATION
                stats.useSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                createRecorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } else {
                null
            }
        if (nextRecorder == null) {
            nextEncoder?.close()
            running.set(false)
            return false
        }

        val nextSocket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("::"), 0))
            }
        } catch (error: Exception) {
            Log.e(TAG, "microphone socket creation failed", error)
            stats.failure(MicrophoneFailureStage.SOCKET_CREATION, error)
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        recorder = nextRecorder
        socket = nextSocket
        opusEncoder = nextEncoder
        return try {
            if (config.audioType == TELEPHONY) {
                echoCanceller = createEchoCanceller()
                if (!speakerphoneCall) effects = voiceEffects(nextRecorder.audioSessionId)
                if (echoReference != null) {
                    Log.i(TAG, "microphone echo canceller enabled=${echoCanceller != null} tail=${ECHO_TAIL_MILLIS}ms")
                }
            }
            nextRecorder.startRecording()
            // startRecording() reports a refusal only through the state. On an MT6765 DiLink 2.0 head unit a call's
            // first start stalled about a second and the first read then failed (#1214), so check before reading.
            val state = nextRecorder.recordingState
            val recording = state == AudioRecord.RECORDSTATE_RECORDING
            if (recording) {
                stats.started(nextRecorder.routedDeviceType())
            } else {
                Log.w(TAG, "microphone did not start recording state=$state")
                stats.failure(MicrophoneFailureStage.RECORDING, code = state)
            }
            // Recovery pauses and rebuilds, so it runs on the capture thread, never on the caller's downlink thread.
            thread = Thread({ capture(nextRecorder, nextSocket, recording) }, "carplay-mic").apply {
                isDaemon = true
                start()
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "microphone recording failed", error)
            stats.failure(MicrophoneFailureStage.RECORDING, error)
            release()
            false
        }
    }

    /** Builds a recorder from [source] at [captureRate]; below the stream's rate, its reads come back upsampled. */
    private fun createRecorder(source: Int): MicrophoneRecorder? {
        val rate = captureRate
        val factor = if (rate == config.sampleRate) 1 else config.sampleRate / rate
        val recorder = try {
            recorderFactory.create(source, rate, channelMask, if (factor == 1) bufferSize else lowRateBufferSize(rate, factor))
        } catch (error: Exception) {
            Log.e(TAG, "microphone recorder creation failed source=$source", error)
            stats.failure(MicrophoneFailureStage.RECORDER_CREATION, error)
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "microphone recorder failed to initialize source=$source")
            stats.failure(MicrophoneFailureStage.RECORDER_INITIALIZATION, code = recorder.state)
            runCatching { recorder.release() }
            return null
        }
        return if (factor == 1) recorder else UpsamplingRecorder(recorder, factor, config.channels)
    }

    /** As in [start], for a recorder at [rate] whose reads [factor] multiplies. */
    private fun lowRateBufferSize(rate: Int, factor: Int): Int {
        val minBuffer = runCatching {
            AudioRecord.getMinBufferSize(rate, channelMask, AndroidAudioFormat.ENCODING_PCM_16BIT)
        }.getOrDefault(0)
        return if (minBuffer > 0) maxOf(minBuffer * 2, config.frameBytes / factor * 4) else bufferSize
    }

    private fun createEchoCanceller(): CallEchoCanceller? {
        val reference = echoReference ?: return null
        if (config.channels != 1 || config.sampleRate != reference.sampleRate) {
            Log.i(TAG, "microphone echo canceller skipped rate=${config.sampleRate} channels=${config.channels}")
            return null
        }
        return echoCancellerFactory(config.frameBytes / 2, config.sampleRate, ECHO_TAIL_MILLIS)
    }

    private fun voiceEffects(sessionId: Int): List<AudioEffect> {
        if (carPhoneState()) {
            // In the car's phone audio state its own processing handles echo and noise, and was only measured
            // with the platform effects off.
            Log.i(TAG, "microphone effects left to the car's phone audio state")
            return emptyList()
        }
        var aec: AudioEffect? = null
        if (echoCanceller != null) {
            // VOICE_COMMUNICATION can enable AEC by default. Keep its controller alive and
            // explicitly disable it while Speex runs, rather than stacking two cancellers.
            val platformAvailable = runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(true)
            if (platformAvailable) {
                aec = configuredEffect("AEC", false) { AcousticEchoCanceler.create(sessionId) }
                if (aec == null) {
                    echoCanceller?.close()
                    echoCanceller = null
                    Log.w(TAG, "microphone native echo canceller skipped: platform AEC could not be disabled")
                }
            }
        }
        if (echoCanceller == null) {
            aec = enabledEffect("AEC") {
                if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId) else null
            }
        }
        val ns = if (echoCanceller != null) {
            // Noise suppression before the canceller distorts the echo it has to model; Speex
            // denoises after cancelling instead. Keep the controller so a fallback can re-enable it.
            configuredEffect("NS", false) {
                if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null
            }
        } else {
            enabledEffect("NS") {
                if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null
            }
        }
        return listOfNotNull(aec, ns)
    }

    // Advertised effects may still fail to initialize on a vendor ROM. Keep recording without them.
    private fun enabledEffect(name: String, create: () -> AudioEffect?): AudioEffect? =
        configuredEffect(name, true, create)

    private fun configuredEffect(name: String, enabled: Boolean, create: () -> AudioEffect?): AudioEffect? {
        var effect: AudioEffect? = null
        try {
            effect = create()
            if (effect != null) {
                val status = effect.setEnabled(enabled)
                if (status == AudioEffect.SUCCESS && effect.enabled == enabled) {
                    Log.i(TAG, "microphone effect=$name enabled=$enabled")
                    return effect
                }
                Log.w(TAG, "microphone effect=$name could not be ${if (enabled) "enabled" else "disabled"} status=$status")
            } else {
                Log.i(TAG, "microphone effect=$name unavailable")
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "microphone effect=$name unavailable; continuing without it", error)
        }
        effect?.let(::releaseEffect)
        return null
    }

    private fun releaseEffect(effect: AudioEffect) {
        try {
            effect.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "microphone effect release failed", error)
        }
    }

    private fun capture(initial: MicrophoneRecorder, socket: DatagramSocket, startedRecording: Boolean) {
        runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO) }
        val frame = ByteArray(config.frameBytes)
        val readBuffer = ByteArray(maxOf(frame.size, MIN_READ_BYTES))
        val counters = MicrophoneCounters()
        var current = initial
        val routeInfo = { current.routedDeviceType() }
        val silence = audioManager?.let { manager -> { silenceDetail(manager, current.audioSessionId) } }
        var canceller = echoCanceller
        var clock = canceller?.let { CaptureClock(config.sampleRate) }
        val reference = canceller?.let { ShortArray(it.frameSamples) }
        var filled = 0
        var noAudioRestarts = 0
        var lastAudioNs = System.nanoTime()
        // Only calls are checked: on #1172 the call's recorder was slow or silent in communication mode, Siri's was not.
        val check = if (config.audioType == TELEPHONY) {
            MicrophoneCaptureCheck(bytesPerSecond = config.sampleRate.toLong() * config.channels * 2)
        } else {
            null
        }

        /** Carries on with [next], which [recover] left recording; a new recorder starts a new frame and clock. */
        fun resume(next: MicrophoneRecorder) {
            lastAudioNs = System.nanoTime()
            check?.reset()
            if (next === current) return
            current = next
            filled = 0
            // Attaching effects to the new recorder's session can drop the native canceller; see voiceEffects.
            if (echoCanceller == null) canceller = null
            clock = canceller?.let { CaptureClock(config.sampleRate) }
        }

        try {
            if (!startedRecording) resume(recover(current, "start", sameRecorder = true) ?: return)
            while (running.get()) {
                stats.reading()
                val count = current.read(readBuffer, 0, readBuffer.size)
                stats.read(count)
                if (count > 0) clock?.read(current, count / 2)
                if (count < 0) {
                    if (!running.get()) return
                    Log.e(TAG, "microphone read failed code=$count")
                    stats.failure(MicrophoneFailureStage.READ, code = count)
                    // The first read after a head unit enters call mode can fail with ERROR_INVALID_OPERATION
                    // (#647), and on some units a restart of the same recorder fails too (#1214). A dead
                    // recorder cannot restart at all, so it goes straight to a new one.
                    val next = recover(current, "read-error", sameRecorder = count != AudioRecord.ERROR_DEAD_OBJECT)
                        ?: return
                    resume(next)
                    continue
                }
                if (count == 0) {
                    stats.flush(routeType = routeInfo, silence = silence)
                    // A recorder that starts with no input device routed returns 0 bytes at once, for
                    // as long as it runs: a Leapmotor T03 (Android 7.1) did this for minutes of Siri and
                    // call capture (#566). Restart it, and pause between empty reads instead of spinning.
                    val now = System.nanoTime()
                    if (running.get() && noAudioRestarts < MAX_NO_AUDIO_RESTARTS &&
                        now - lastAudioNs >= noAudioRestartMillis * 1_000_000L
                    ) {
                        noAudioRestarts++
                        lastAudioNs = now
                        if (restartRecording(current)) {
                            check?.reset()
                            stats.restarted(noAudioRestarts, "no-audio", current.routedDeviceType())
                        } else {
                            stats.failure(MicrophoneFailureStage.RECORDING, code = current.recordingState)
                        }
                    } else {
                        Thread.sleep(EMPTY_READ_PAUSE_MILLIS)
                    }
                    continue
                }
                val verdict = check?.read(readBuffer, count, captureCheckClock(), audioMode)
                if (verdict != null) {
                    val next = afterCaptureCheck(current, verdict) ?: return
                    if (next !== current) {
                        // The rest of the old recorder's read is dropped with its partial frame.
                        resume(next)
                        continue
                    }
                }
                lastAudioNs = System.nanoTime()
                var offset = 0
                while (offset < count && running.get()) {
                    val copied = minOf(frame.size - filled, count - offset)
                    readBuffer.copyInto(frame, filled, offset, offset + copied)
                    filled += copied
                    offset += copied
                    if (filled == frame.size) {
                        val activeCanceller = canceller
                        val activeClock = clock
                        if (activeCanceller != null && activeClock != null && reference != null) {
                            // Ask slightly ahead of the frame so speaker latency the clock misses stays inside the tail.
                            echoReference?.read(reference, activeClock.frameEndNs(offset / 2) + ECHO_REFERENCE_LEAD_NS)
                            val processed = try {
                                activeCanceller.process(frame, reference)
                            } catch (error: RuntimeException) {
                                Log.w(TAG, "microphone native echo processing failed; restoring platform AEC", error)
                                false
                            } catch (error: LinkageError) {
                                Log.w(TAG, "microphone native echo processing unavailable; restoring platform AEC", error)
                                false
                            }
                            if (!processed) {
                                restorePlatformEchoCancellation()
                                canceller = null
                            }
                        }
                        stats.level(frame)
                        sendFrame(socket, counters, frame)
                        filled = 0
                    }
                }
                stats.flush(routeType = routeInfo, silence = silence)
            }
        } catch (error: Exception) {
            if (running.get()) {
                Log.e(TAG, "microphone capture failed", error)
                stats.failure(MicrophoneFailureStage.CAPTURE, error)
            }
        } finally {
            stats.flush(ended = true, routeType = routeInfo, silence = silence)
            running.set(false)
            release()
        }
    }

    /**
     * Gets capture recording again after [failed] stopped: [reason] is "start" when its first startRecording did
     * not take, "read-error", or the verdict of a call's [MicrophoneCaptureCheck] when [afterCaptureCheck] replaces a
     * recorder that records too slowly or silently. When [sameRecorder] allows, restarts [failed] once, which was
     * enough for #647.
     * Then releases it and builds new recorders after each pause in [rebuildBackoffMillis], because on #1214 the
     * same recorder never came back. Every attempt counts toward [MAX_RECOVERY_ATTEMPTS] for the stream, whether
     * it works or not, so a recorder that keeps failing ends capture instead of looping for the whole call.
     *
     * Returns the recorder that is now recording, or null when the stream closed or the attempts ran out.
     */
    private fun recover(failed: MicrophoneRecorder, reason: String, sameRecorder: Boolean): MicrophoneRecorder? {
        if (sameRecorder && running.get() && recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
            val attempt = ++recoveryAttempts
            if (restartRecording(failed)) {
                stats.restarted(attempt, reason, failed.routedDeviceType())
                return failed
            }
            // A stream that closed during a stalled start was stopped by close(); that is not a failure.
            if (running.get()) stats.restartFailed(attempt, reason, newRecorder = false, state = failed.recordingState)
        }
        retire(failed)
        for (pause in rebuildBackoffMillis) {
            if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) break
            if (!pauseWhileRunning(pause)) return null
            fallBackFromCommunicationSource()
            val attempt = ++recoveryAttempts
            val next = createRecorder(source)
            if (next == null) {
                if (running.get()) stats.restartFailed(attempt, reason, newRecorder = true, state = null)
                rebuildFailed()
                leaveRejectedRecognitionSource()
                leaveRejectedCaptureRate()
                continue
            }
            if (!install(next)) {
                runCatching { next.release() }
                return null
            }
            val state = try {
                next.startRecording()
                next.recordingState
            } catch (error: IllegalStateException) {
                Log.w(TAG, "microphone rebuilt recorder failed to start", error)
                null
            }
            if (state == AudioRecord.RECORDSTATE_RECORDING) {
                failedCommunicationRecorders = 0
                stats.restarted(attempt, reason, next.routedDeviceType(), newRecorder = true,
                    platformEffects = enabledEffectNames(),
                    diplayEchoCanceller = if (config.audioType == TELEPHONY) echoCanceller != null else null)
                return next
            }
            if (running.get()) stats.restartFailed(attempt, reason, newRecorder = true, state = state)
            rebuildFailed()
            retire(next)
        }
        if (running.get()) stats.recoveryEnded(recoveryAttempts)
        return null
    }

    private fun rebuildFailed() {
        if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) failedCommunicationRecorders++
    }

    /**
     * As in [start]: a unit that cannot even build a VOICE_RECOGNITION recorder won't on the next try either,
     * so the remaining attempts go back to VOICE_COMMUNICATION instead of spending the budget on that source.
     * A call that left VOICE_COMMUNICATION because it recorded too slowly or silently goes on to MIC instead,
     * except in the car's phone audio state, which mutes that source (see [telephonyMicrophoneSource]).
     */
    private fun leaveRejectedRecognitionSource() {
        if (source != MediaRecorder.AudioSource.VOICE_RECOGNITION) return
        recognitionRejected = true
        source = if (communicationCaptureFailed && !carPhoneState()) {
            MediaRecorder.AudioSource.MIC
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
        stats.useSource(source)
    }

    /** A unit that cannot build a recorder at [VOCODER_RATE] gets the remaining attempts at the stream's rate. */
    private fun leaveRejectedCaptureRate() {
        if (captureRate == config.sampleRate) return
        useCaptureRate(config.sampleRate)
    }

    private fun useCaptureRate(rate: Int) {
        captureRate = rate
        stats.useCaptureRate(rate)
    }

    /**
     * Acts on a call's [MicrophoneCaptureCheck] (#1172) and returns the recorder to carry on with: [current], or a
     * new one that [recover] built, or null when capture ends. A recorder that is slow or silent is rebuilt at most
     * twice per stream, so a call never loops: first at [VOCODER_RATE] from the same source, which is what the
     * #1172 recorder delivered anyway, then once from [captureFallbackSource] at the stream's rate, as Siri records
     * on that unit. A step that does not apply is skipped, and each rebuild counts toward [MAX_RECOVERY_ATTEMPTS].
     * Every check reports one 'capture check' line with its verdict and action.
     */
    private fun afterCaptureCheck(current: MicrophoneRecorder, result: MicrophoneCaptureCheck.Result): MicrophoneRecorder? {
        val step = if (result.verdict == MicrophoneCaptureCheck.Verdict.OK) null else nextCaptureStep()
        stats.captureCheck(result, nextSource = step?.source, nextCaptureRate = step?.rate)
        if (step == null) return current
        Log.w(TAG, "microphone call capture ${result.verdict.label} at ${result.realtimePercent}% of real time; " +
            "rebuilding source=${step.source} rate=${step.rate}")
        if (step.source != source) {
            if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) communicationCaptureFailed = true
            source = step.source
            stats.useSource(source)
        }
        useCaptureRate(step.rate)
        return recover(current, result.verdict.label, sameRecorder = false)
    }

    private class CaptureStep(val source: Int, val rate: Int)

    private fun nextCaptureStep(): CaptureStep? {
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) return null
        if (!vocoderRateTried) {
            vocoderRateTried = true
            val rate = config.sampleRate
            if (captureRate == rate && rate > VOCODER_RATE && rate % VOCODER_RATE == 0) return CaptureStep(source, VOCODER_RATE)
        }
        if (!captureSourceChanged) {
            captureSourceChanged = true
            captureFallbackSource()?.let { return CaptureStep(it, config.sampleRate) }
        }
        return null
    }

    /**
     * The source after a call's recorder stayed slow or silent: VOICE_RECOGNITION, which recorded Siri at full rate
     * with sound on the #1172 head unit, or MIC where this unit refused VOICE_RECOGNITION. Geely/Lynk firmware only
     * records from VOICE_COMMUNICATION, and the car's phone audio state mutes MIC, so neither gets a new source.
     */
    private fun captureFallbackSource(): Int? {
        if (geelyHeadUnit()) return null
        val micAllowed = !carPhoneState()
        return when (source) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> when {
                !recognitionRejected -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                micAllowed -> MediaRecorder.AudioSource.MIC
                else -> null
            }
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> if (micAllowed) MediaRecorder.AudioSource.MIC else null
            else -> null
        }
    }

    /**
     * After [FALLBACK_AFTER_FAILED_RECORDERS] new VOICE_COMMUNICATION recorders in a row fail on a call, the rest
     * are built from VOICE_RECOGNITION, which recorded Siri on the #1214 head unit. That source gets no platform
     * echo canceller by default, so the far end may hear some echo; that is chosen over a call it cannot hear.
     * The 'recording restarted' line of the new recorder says which effects and canceller it got once they were
     * attached. Geely/Lynk units record silence on VOICE_RECOGNITION (see [telephonyMicrophoneSource]), and a
     * unit that refused to build VOICE_RECOGNITION in [start] would refuse it again, so both keep VOICE_COMMUNICATION.
     */
    private fun fallBackFromCommunicationSource() {
        if (config.audioType != TELEPHONY || source != MediaRecorder.AudioSource.VOICE_COMMUNICATION) return
        if (failedCommunicationRecorders < FALLBACK_AFTER_FAILED_RECORDERS || geelyHeadUnit() || recognitionRejected) return
        Log.w(TAG, "microphone call source falls back to VOICE_RECOGNITION after $failedCommunicationRecorders failed recorders")
        stats.sourceFallback(
            from = source,
            to = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            failedRecorders = failedCommunicationRecorders,
        )
        source = MediaRecorder.AudioSource.VOICE_RECOGNITION
        stats.useSource(source)
    }

    /** Makes [next] the stream's recorder, with the call's effects on its session; false once the stream closed. */
    @Synchronized
    private fun install(next: MicrophoneRecorder): Boolean {
        if (!running.get()) return false
        recorder = next
        if (config.audioType == TELEPHONY && !speakerphoneCall) effects = voiceEffects(next.audioSessionId)
        return true
    }

    /** Releases [old] and the effects on its session; [install] attaches new ones to the next recorder. */
    private fun retire(old: MicrophoneRecorder) {
        val oldEffects = synchronized(this) {
            if (recorder === old) recorder = null
            effects.also { effects = emptyList() }
        }
        oldEffects.forEach(::releaseEffect)
        runCatching { old.stop() }
        runCatching { old.release() }
    }

    private fun enabledEffectNames(): String {
        val names = effects.filter { runCatching { it.enabled }.getOrDefault(false) }.map {
            when (it) {
                is AcousticEchoCanceler -> "AEC"
                is NoiseSuppressor -> "NS"
                else -> "other"
            }
        }
        return if (names.isEmpty()) "none" else names.joinToString(",")
    }

    /** Waits [millis] in short steps so a closing stream is not held up; false when it closed meanwhile. */
    private fun pauseWhileRunning(millis: Long): Boolean {
        val end = System.nanoTime() + millis * 1_000_000L
        while (running.get()) {
            val left = (end - System.nanoTime()) / 1_000_000L
            if (left <= 0) return true
            Thread.sleep(minOf(left, PAUSE_STEP_MILLIS))
        }
        return false
    }

    private fun restartRecording(recorder: MicrophoneRecorder): Boolean = try {
        runCatching { recorder.stop() }
        Thread.sleep(READ_RESTART_PAUSE_MILLIS)
        recorder.startRecording()
        recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
    } catch (error: IllegalStateException) {
        false
    }

    private fun sendFrame(socket: DatagramSocket, counters: MicrophoneCounters, frame: ByteArray) {
        val bodies = if (config.codec == AudioCodecKind.OPUS) {
            opusEncoder?.encode(frame).orEmpty()
        } else {
            listOf(MicrophonePacketizer.toWirePcm(frame))
        }
        stats.encoded(bodies.size, if (bodies.isEmpty()) 1 else bodies.count { it.isEmpty() })
        bodies.forEach { body ->
            sendPacket(
                socket = socket,
                counters = counters,
                body = body,
                samples = config.rtpSamplesPerPacket,
            )
        }
    }

    private fun sendPacket(
        socket: DatagramSocket,
        counters: MicrophoneCounters,
        body: ByteArray,
        samples: Int,
    ) {
        val packet = MicrophonePacketizer.sealPacket(
            key = config.key,
            payloadType = config.payloadType,
            counters = counters,
            body = body,
            samples = samples,
        )
        try {
            socket.send(DatagramPacket(packet, packet.size, config.host, config.port))
            stats.sent()
        } catch (error: Exception) {
            stats.sendFailed()
            if (running.get()) throw error
        }
    }

    /**
     * The audio mode and the sources of the other recordings running now. A head unit that stays in
     * communication mode after a call, or another app holding the microphone, can each leave this one silent.
     * Other apps' recordings carry no package name for a normal app, only their source.
     */
    private fun silenceDetail(manager: AudioManager, ownSession: Int): String {
        val others = manager.activeRecordingConfigurations
            .filter { it.clientAudioSessionId != ownSession }
            .map { MicrophoneCaptureStats.sourceName(it.clientAudioSource) }
        return "audioMode=${manager.mode} otherRecordings=" +
            if (others.isEmpty()) "none" else others.take(MAX_LISTED_RECORDINGS).joinToString(",", "[", "]")
    }

    /** Capture can continue with the same recorder if the optional native processor stops working. */
    @Synchronized
    private fun restorePlatformEchoCancellation() {
        val current = echoCanceller ?: return
        echoCanceller = null
        current.close()
        if (!running.get()) return // close/release already owns all recorder effects.
        effects.filterIsInstance<NoiseSuppressor>().forEach { ns ->
            runCatching { ns.setEnabled(true) }
                .onFailure { Log.w(TAG, "microphone platform NS could not be restored", it) }
        }
        val aec = effects.filterIsInstance<AcousticEchoCanceler>().firstOrNull()
        if (aec != null) {
            val enabled = runCatching { aec.setEnabled(true) == AudioEffect.SUCCESS && aec.enabled }.getOrDefault(false)
            if (enabled) {
                Log.i(TAG, "microphone native echo processing stopped; platform AEC restored")
                return
            }
            effects = effects - aec
            releaseEffect(aec)
        }
        enabledEffect("AEC") {
            val session = recorder?.audioSessionId
            if (session != null && AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session) else null
        }?.let { effects = effects + it }
        Log.i(TAG, "microphone native echo processing stopped; using platform effects")
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            release()
            return
        }
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Best effort; release below is authoritative.
        }
        try {
            socket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        thread?.let { worker ->
            try {
                worker.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (worker.isAlive) worker.interrupt()
        }
        release()
    }

    @Synchronized
    private fun release() {
        running.set(false)
        val currentEffects = effects
        effects = emptyList()
        currentEffects.forEach(::releaseEffect)
        echoCanceller?.close()
        echoCanceller = null
        val currentRecorder = recorder
        recorder = null
        try {
            currentRecorder?.release()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentSocket = socket
        socket = null
        try {
            currentSocket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentEncoder = opusEncoder
        opusEncoder = null
        currentEncoder?.close()
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val MIN_READ_BYTES = 2_048
        const val CLOSE_JOIN_MILLIS = 500L
        const val ECHO_TAIL_MILLIS = 250
        const val ECHO_REFERENCE_LEAD_NS = 30_000_000L
        const val TELEPHONY = "telephony"
        const val READ_RESTART_PAUSE_MILLIS = 50L
        /** Per stream; one failure uses at most one restart plus one new recorder per [REBUILD_BACKOFF_MILLIS]. */
        const val MAX_RECOVERY_ATTEMPTS = 8
        val REBUILD_BACKOFF_MILLIS = listOf(250L, 500L, 1_000L, 1_000L)
        const val FALLBACK_AFTER_FAILED_RECORDERS = 2
        /** The iPhone's wideband call vocoder rate; #1172's 48 kHz recorder delivered about this many samples a second. */
        const val VOCODER_RATE = 16_000
        const val PAUSE_STEP_MILLIS = 50L
        const val NO_AUDIO_RESTART_MILLIS = 1_000L
        const val MAX_NO_AUDIO_RESTARTS = 3
        const val EMPTY_READ_PAUSE_MILLIS = 5L
        const val MAX_LISTED_RECORDINGS = 4
    }
}

/**
 * The recorder source for a CarPlay call. While DiPlay holds the car's phone audio state the car's call
 * processing is on for every source, but VOICE_COMMUNICATION then clips (about a fifth of the samples on a
 * Han) and the plain microphone source is muted. VOICE_RECOGNITION, which is also what the stock CarPlay
 * service ends up with, gives a normal level with no clipping, and the car still suppresses the far end's
 * echo on it. Geely/Lynk head units record silence on VOICE_RECOGNITION, so they keep the old choice.
 */
internal fun telephonyMicrophoneSource(speakerphoneCall: Boolean, carPhoneState: Boolean, geelyHeadUnit: Boolean): Int = when {
    carPhoneState && !geelyHeadUnit -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    speakerphoneCall -> MediaRecorder.AudioSource.MIC
    else -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
}

/**
 * When each captured microphone sample was heard, on System.nanoTime's clock. Uses the recorder's
 * timestamp when the platform has one, otherwise assumes the newest sample arrived [FALLBACK_LATENCY_NS] ago.
 */
internal class CaptureClock(private val sampleRate: Int) {
    private val timestamp = android.media.AudioTimestamp()
    private var samplesRead = 0L
    private var lastReadSamples = 0
    private var lastReadEndNs = 0L

    fun read(recorder: MicrophoneRecorder, samples: Int) {
        samplesRead += samples
        lastReadSamples = samples
        val now = System.nanoTime()
        val stamped = runCatching { recorder.timestamp(timestamp) }.getOrDefault(false)
        lastReadEndNs = if (stamped && timestamp.nanoTime > 0) {
            timestamp.nanoTime + (samplesRead - timestamp.framePosition) * 1_000_000_000L / sampleRate
        } else {
            now - FALLBACK_LATENCY_NS
        }
    }

    /** The capture time of the end of the first [samplesIntoRead] samples of the latest read. */
    fun frameEndNs(samplesIntoRead: Int): Long =
        lastReadEndNs - (lastReadSamples - samplesIntoRead).coerceAtLeast(0) * 1_000_000_000L / sampleRate

    private companion object {
        const val FALLBACK_LATENCY_NS = 20_000_000L
    }
}

/** The part of [AudioRecord] that microphone capture uses, so tests can stand in a recorder that fails. */
internal interface MicrophoneRecorder {
    /** [AudioRecord.STATE_INITIALIZED] once the platform accepted the recorder. */
    val state: Int
    val recordingState: Int
    val audioSessionId: Int

    /** The routed input device's type, or null when the platform cannot tell. */
    fun routedDeviceType(): Int?

    /** Like [AudioRecord.startRecording], a refusal shows only in [recordingState]. */
    fun startRecording()
    fun stop()

    /** A blocking read: the bytes read, 0, or a negative [AudioRecord] error code. */
    fun read(buffer: ByteArray, offset: Int, size: Int): Int

    /** Fills [into] on the monotonic clock; false when the platform has no timestamp. */
    fun timestamp(into: AudioTimestamp): Boolean
    fun release()
}

/** Builds a recorder, or throws when the platform rejects the source, like [AudioRecord.Builder.build]. */
internal fun interface MicrophoneRecorderFactory {
    fun create(source: Int, sampleRate: Int, channelMask: Int, bufferSize: Int): MicrophoneRecorder
}

internal object PlatformMicrophoneRecorders : MicrophoneRecorderFactory {
    override fun create(source: Int, sampleRate: Int, channelMask: Int, bufferSize: Int): MicrophoneRecorder =
        PlatformMicrophoneRecorder(
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AndroidAudioFormat.Builder()
                        .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build(),
        )
}

private class PlatformMicrophoneRecorder(private val record: AudioRecord) : MicrophoneRecorder {
    override val state: Int get() = record.state
    override val recordingState: Int get() = record.recordingState
    override val audioSessionId: Int get() = record.audioSessionId

    override fun routedDeviceType(): Int? = runCatching { record.routedDevice?.type }.getOrNull()

    override fun startRecording() = record.startRecording()

    override fun stop() = record.stop()

    override fun read(buffer: ByteArray, offset: Int, size: Int): Int =
        record.read(buffer, offset, size, AudioRecord.READ_BLOCKING)

    override fun timestamp(into: AudioTimestamp): Boolean =
        record.getTimestamp(into, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS

    override fun release() = record.release()
}
