package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.MfiAuthenticator

/**
 * Shared scripted-session fixture for the wired and wireless control-loop tests.
 *
 * Iap2Session/Iap2CsmChannel are final with private constructors, so the in-memory fake is
 * assembled reflectively: a passive link (worker never started) whose recv drains pre-queued
 * frames and whose send accepts every frame. All writes are observable through the session
 * onTrace callback. A short run timeout makes the final recv return null so run() ends in
 * TIMED_OUT after the scripted frames.
 */
internal object Iap2ControlClientTestHarness {
    fun scriptedSession(availabilityFrames: Int, onTrace: (String) -> Unit): Iap2Session {
        val frames = mutableListOf(
            Iap2Frame(0x1d00, byteArrayOf()), // StartIdentification -> reply 0x1d01
            Iap2Frame(0x1d02, byteArrayOf()), // IdentificationAccepted -> done
            Iap2Frame(0xaa00, byteArrayOf()), // RequestCertificate -> reply 0xaa01
            Iap2Frame(0xaa05, byteArrayOf()), // AuthenticationSucceeded -> done
        )
        // An empty body parses as an empty parameter list. A one-byte body makes
        // Iap2WiredControlClient.carPlayAvailabilitySummary() throw "Truncated CSM parameter header".
        repeat(availabilityFrames) { frames += Iap2Frame(0x4300, byteArrayOf()) }
        return Iap2Session.wrap(reflectiveChannel(reflectiveLink(), frames), "test", onTrace)
    }

    fun fakeMfi(): Iap2MfiAuthenticationClient =
        Iap2MfiAuthenticationClient(object : MfiAuthenticator {
            override fun protocolMajor(): Int = 1
            override fun readCertificate(maximumOutputLength: Int): ByteArray = byteArrayOf(1, 2, 3)
            override fun signChallenge(challenge: ByteArray): ByteArray = byteArrayOf(4, 5, 6)
        })

    private fun reflectiveLink(): Iap2LinkChannel {
        val onArtwork: (Iap2ArtworkTransfer) -> Unit = {}
        val constructor = Iap2LinkChannel::class.java.declaredConstructors
            .first { it.parameterCount == 4 }
            .apply { isAccessible = true }
        val link = constructor.newInstance(
            fakeStream(),
            Iap2LinkConfig(),
            false,
            onArtwork,
        ) as Iap2LinkChannel
        accessibleField(link, "ready").set(link, true)
        accessibleField(link, "peerMaxControlPayloadBytes").set(link, 65_525)
        return link
    }

    @Suppress("UNCHECKED_CAST")
    private fun reflectiveChannel(link: Iap2LinkChannel, frames: List<Iap2Frame>): Iap2CsmChannel {
        val constructor = Iap2CsmChannel::class.java.declaredConstructors
            .first { it.parameterCount == 1 }
            .apply { isAccessible = true }
        val channel = constructor.newInstance(link) as Iap2CsmChannel
        val queued = accessibleField(channel, "receivedFrames").get(channel) as java.util.ArrayDeque<Iap2Frame>
        frames.forEach { queued.add(it) }
        return channel
    }

    private fun accessibleField(target: Any, name: String) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun fakeStream(): BlockingDuplexByteStream = object : BlockingDuplexByteStream {
        override fun send(data: ByteArray) {}
        override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? = null
        override fun close() {}
    }
}
