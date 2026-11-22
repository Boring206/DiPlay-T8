package com.shilapi.xcertplay

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.util.UUID

/**
 * Report text for video in car (#1180). A media URL can carry a session token in its path or query,
 * so these lines keep only the scheme of a URL (and the domain of a web one), protocol keys, sizes and
 * error codes. Exception messages stay out as well: players put the failing URL in them.
 */
internal object VideoDiagnostics {
    private const val MAX_WORD = 64
    private const val MAX_KEYS = 12
    private val unsafeWordCharacters = Regex("[^A-Za-z0-9_.:-]")

    fun queueItem(app: Any?, url: String, mediaType: Any?): String =
        "queue item app=${word(app)} scheme=${scheme(url)}" + (host(url)?.let { " host=$it" } ?: "") +
            " mediaType=${word(mediaType)}"

    fun scheme(url: String): String = word(runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull())

    /**
     * The domain of an http or https URL: its last two labels (rr3---sn-abc.googlevideo.com becomes
     * googlevideo.com), since a CDN node name hints at where the viewer is, and "ip" for an address.
     * Null for an app's own scheme: the app can put an id or a token where the host would be.
     */
    fun host(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in WEB_SCHEMES) return null
        val host = uri.host?.lowercase()?.trim('.')?.takeIf { it.isNotEmpty() } ?: return null
        if (':' in host || host.startsWith('[') || host.all { it.isDigit() || it == '.' }) return "ip"
        return word(host.split('.').filter { it.isNotEmpty() }.takeLast(2).joinToString("."))
    }

    /** A protocol word (a message type, a media type, an app id) cut to plain characters. */
    fun word(value: Any?): String {
        val text = value?.toString()?.replace(unsafeWordCharacters, "")?.take(MAX_WORD)
        return if (text.isNullOrEmpty()) "none" else text
    }

    fun keys(keys: Collection<*>): String {
        val words = keys.take(MAX_KEYS).map(::word)
        val more = if (keys.size > MAX_KEYS) ",+${keys.size - MAX_KEYS}" else ""
        return words.joinToString(",", "[", "$more]")
    }

    /** "player error <name> code=<n>", the HTTP status when there is one, and the cause types. */
    fun playerError(error: PlaybackException): String {
        val chain = generateSequence<Throwable>(error) { it.cause }.take(5).toList()
        val http = chain.firstNotNullOfOrNull { (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode }
        return "player error ${error.errorCodeName} code=${error.errorCode}" +
            (http?.let { " http=$it" } ?: "") +
            " causes=" + chain.drop(1).joinToString("<-") { it.javaClass.simpleName }.ifEmpty { "none" }
    }

    fun loadingVideo(url: String, network: String): String = "loading video scheme=${scheme(url)} $network"

    fun iphoneLoaded(scheme: String?, status: Int?, bytes: Int?, redirect: Boolean): String =
        "iPhone loaded ${word(scheme)} URL status=$status bytes=$bytes redirect=$redirect"

    /** Which encryption an HLS stream uses; key URIs reduced to their scheme, DRM systems to names. */
    fun hlsEncryption(
        keyScheme: String?,
        sampleAesSchemeType: String?,
        drm: List<UUID>?,
        sessionKeys: List<String?>,
        segments: Int,
    ): String =
        "HLS encryption aes128KeyScheme=${keyScheme?.let(::word)} sampleAesSchemeType=${sampleAesSchemeType?.let(::word)} " +
            "drm=${drm?.joinToString(",", "[", "]") { drmName(it) }} " +
            "sessionKeys=${sessionKeys.joinToString(",", "[", "]") { word(it) }} segments=$segments"

    /** A UUID would be masked as an identifier, so the known systems go by name. */
    fun drmName(uuid: UUID): String = when (uuid) {
        C.WIDEVINE_UUID -> "widevine"
        C.PLAYREADY_UUID -> "playready"
        C.CLEARKEY_UUID -> "clearkey"
        C.COMMON_PSSH_UUID -> "common-pssh"
        FAIRPLAY_UUID -> "fairplay"
        else -> "other"
    }

    private val FAIRPLAY_UUID: UUID = UUID.fromString("94ce86fb-07ff-4f43-adb8-93d2fa968ca2")
    private val WEB_SCHEMES = setOf("http", "https")

    /**
     * Keeps the per-load lines of an item loaded through the iPhone (asked, answer, loaded) from filling
     * the report: an app-scheme playlist can resolve every segment that way. The first [limit] lines of
     * each kind are written in full; [finish] then gives one line with the counts. Any thread.
     */
    class IphoneLoads(private val limit: Int = 3) {
        enum class Kind(val label: String) {
            ASKED("asked"), ANSWER("answers"), LOADED("loaded"), FAILED("failed"), NO_ANSWER("noAnswer"),
        }

        private val counts = IntArray(Kind.entries.size)

        /** Counts one line of [kind]; true while it is still one to write in full. */
        @Synchronized
        fun take(kind: Kind): Boolean = ++counts[kind.ordinal] <= limit

        /** The summary of the item that ended, or null when every line was written; starts the next item. */
        @Synchronized
        fun finish(): String? {
            val summary = if (counts.any { it > limit }) {
                "iPhone loads for the item " + Kind.entries.joinToString(" ") { "${it.label}=${counts[it.ordinal]}" } +
                    " (first $limit of each kind written in full)"
            } else {
                null
            }
            counts.fill(0)
            return summary
        }
    }
}
