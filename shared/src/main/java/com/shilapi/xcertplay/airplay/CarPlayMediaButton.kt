package com.shilapi.xcertplay.airplay

import android.view.KeyEvent

/**
 * Hardware media keys → CarPlay media HID presses (indices into [AirPlayHid]'s media report).
 *
 * Hardware play and pause keys both map to the toggle: BYD picks PLAY or PAUSE from its own idea of
 * the play state, and a wrong guess would make the button do nothing. Explicit play and pause
 * commands from media controllers use [PLAY] and [PAUSE].
 */
object CarPlayMediaButton {
    const val PLAY = 1
    const val PAUSE = 2
    const val PLAY_PAUSE = 3
    const val NEXT = 4
    const val PREVIOUS = 5

    /**
     * Fast Forward and Rewind, held while the iPhone seeks. The media HID device declares them only when
     * [AirPlayConfig.mediaSeekKeys] is on (indices 7 and 8 of [AirPlayHid]'s media report).
     */
    const val FAST_FORWARD = 7
    const val REWIND = 8

    /**
     * DiLink 3's long-press next and previous keys (#1190). The wheel sends a long press as a key of its own.
     * Their numbers are not in the SDK; BYD's framework knows them by these names.
     */
    const val KEYCODE_NAME_BYD_MEDIA_NEXT_LONG = "KEYCODE_AUTO_MEDIA_NEXT_LP"
    const val KEYCODE_NAME_BYD_MEDIA_PREVIOUS_LONG = "KEYCODE_AUTO_MEDIA_PREVIOUS_LP"

    /** The long-press keys of this head unit, looked up by name on first use. */
    val longPressSeekKeys = LongPressSeekKeys(KeyEvent::keyCodeFromString, KeyEvent::keyCodeToString)

    /** BYD's steering-wheel play/pause key; the firmware normally rewrites it to MEDIA_PLAY/PAUSE. */
    const val KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE = 353

    /** BYD's steering-wheel voice key: a short press, and the code the wheel sends for a long press. */
    const val KEYCODE_BYD_AUTO_MEDIA_VOICE = 304
    const val KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG = 312

    /** DiLink 3's play/pause key (KEYCODE_AUTO_MEDIA_PLAY_PAUSE), when the firmware does not rewrite it. */
    const val KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE_DILINK3 = 331

    /**
     * DiLink 3's CarPlay voice key, short and long. BYD's window manager keeps it for its own CarPlay
     * app, so only the wheel key service sees it (see [opensSiriWhileCarPlay]).
     */
    const val KEYCODE_BYD_CARPLAY_VOICE = 327
    const val KEYCODE_BYD_CARPLAY_VOICE_LONG = 328

    /**
     * Whether [keyCode] is a voice key that opens Siri. The BYD wheel sends each press as an
     * instant down/up pair, so a long press arrives as its own key rather than as a held one.
     */
    fun opensSiri(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
        keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE || keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG

    /** The voice keys BYD sends only for CarPlay; they open Siri while a CarPlay session runs. */
    fun opensSiriWhileCarPlay(keyCode: Int): Boolean =
        keyCode == KEYCODE_BYD_CARPLAY_VOICE || keyCode == KEYCODE_BYD_CARPLAY_VOICE_LONG

    /** The two voice keys the wheel sends for a short press of its voice button. */
    fun isShortVoiceKey(keyCode: Int): Boolean =
        keyCode == KEYCODE_BYD_CARPLAY_VOICE || keyCode == KEYCODE_BYD_AUTO_MEDIA_VOICE

    /** The CarPlay press for [keyCode], or null when the key is not a media key CarPlay handles. */
    fun forKeyCode(keyCode: Int, experimentalDiLink3Keys: Boolean = false): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PREVIOUS
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE -> PLAY_PAUSE
        KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE_DILINK3 -> if (experimentalDiLink3Keys) PLAY_PAUSE else null
        else -> null
    }

    /**
     * The CarPlay press the CarPlay screen itself should claim for [keyCode], or null to let the key
     * go on to the car.
     *
     * BYD hands the steering wheel's next/previous keys to whichever window is in front, and DiPlay's
     * CarPlay screen is that window whenever the driver is looking at it. Claiming them here is what
     * makes the wheel change tracks without the accessibility service, which a head unit with no ADB
     * cannot turn on.
     *
     * Only next and previous are claimed. Play/pause is left alone: BYD acts on it itself, and the
     * wheel key service owns it for DiLink 3. Nothing is claimed outside a CarPlay session, or during
     * a call, where the car's own call handling is what the driver expects from these keys.
     */
    fun forCarPlayWindow(
        keyCode: Int,
        sessionActive: Boolean,
        inCall: Boolean,
    ): Int? {
        if (!sessionActive || inCall) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PREVIOUS
            else -> null
        }
    }
}

/**
 * The numbers this head unit gives [CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_NEXT_LONG] and
 * [CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_PREVIOUS_LONG]. [fromName] is KeyEvent.keyCodeFromString and
 * [toName] KeyEvent.keyCodeToString.
 *
 * From Android 10, keyCodeFromString gives KEYCODE_UNKNOWN for a code above the SDK's last key code, and
 * BYD's own codes are above it; keyCodeToString has no such limit, and it named the long-press keys in the
 * #1190 reports. So a key is matched by its name, and a name that keyCodeFromString does not resolve is
 * looked for once among codes 1 to [LAST_SCANNED_CODE]. A firmware that names neither key maps nothing.
 */
class LongPressSeekKeys(private val fromName: (String) -> Int, private val toName: (Int) -> String) {
    private val codes: Map<String, Int> by lazy(::lookUp)
    private val next get() = codes[CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_NEXT_LONG]
    private val previous get() = codes[CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_PREVIOUS_LONG]

    /** Whether this head unit's framework knows either long-press key. */
    val available: Boolean get() = codes.isNotEmpty()

    /** For the diagnostic report: the numbers this firmware gives the keys. */
    fun describe(): String =
        if (available) "next=${next ?: "none"} previous=${previous ?: "none"}" else "not named by this firmware"

    /** [CarPlayMediaButton.FAST_FORWARD] or [CarPlayMediaButton.REWIND] for a long-press key, else null. */
    fun seekFor(keyCode: Int): Int? = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) null else when (name(keyCode)) {
        CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_NEXT_LONG -> CarPlayMediaButton.FAST_FORWARD
        CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_PREVIOUS_LONG -> CarPlayMediaButton.REWIND
        else -> null
    }

    private fun name(code: Int): String? = runCatching { toName(code) }.getOrNull()

    private fun lookUp(): Map<String, Int> {
        val wanted = listOf(CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_NEXT_LONG, CarPlayMediaButton.KEYCODE_NAME_BYD_MEDIA_PREVIOUS_LONG)
        val found = LinkedHashMap<String, Int>()
        for (name in wanted) {
            runCatching { fromName(name) }.getOrNull()?.takeIf { it != KeyEvent.KEYCODE_UNKNOWN }?.let { found[name] = it }
        }
        if (found.size < wanted.size) {
            for (code in 1..LAST_SCANNED_CODE) {
                val name = name(code)?.takeIf { it in wanted && it !in found } ?: continue
                found[name] = code
                if (found.size == wanted.size) break
            }
        }
        return found
    }

    companion object {
        /** Far above AOSP's last key code (288 in Android 10) and BYD's known codes (304 to 353). */
        const val LAST_SCANNED_CODE = 1023
    }
}
