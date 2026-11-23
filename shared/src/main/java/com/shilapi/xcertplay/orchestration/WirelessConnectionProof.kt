package com.shilapi.xcertplay.orchestration

/**
 * Confirms one current session only after authenticated tunnel readiness AND a rendered frame.
 *
 * Separately, [isUsable] says whether that session works without the tunneled iAP2 channel, which
 * decides whether the handoff watchdog keeps it.
 */
internal class WirelessConnectionProof<S : Any> {
    private var generation = -1
    private var session: S? = null
    private var authenticated = false
    private var rendered = false
    // Kept once [isUsable] has accepted the current session, so a later check cannot drop it.
    private var usable = false
    // A main-screen surface was attached at some point during the current session (see observeScreen).
    private var screenShown = false
    // The main screen's attachment count at the session's first observation; null before it.
    private var screenAttachmentsAtStart: Long? = null
    private var confirmation: (() -> Unit)? = null

    @Synchronized fun begin(generation: Int, confirmation: () -> Unit) {
        clear()
        this.generation = generation
        this.confirmation = confirmation
    }

    @Synchronized fun activate(generation: Int, session: S) {
        if (this.generation != generation || this.session === session) return
        this.session = session
        authenticated = false
        rendered = false
        usable = false
        screenShown = false
        screenAttachmentsAtStart = null
    }

    @Synchronized fun authenticated(generation: Int) {
        if (this.generation != generation || session == null) return
        authenticated = true
        confirmIfReady()
    }

    @Synchronized fun rendered(generation: Int, session: S) {
        if (this.generation != generation || this.session !== session) return
        rendered = true
        confirmIfReady()
    }


    @Synchronized fun hasActiveSession(generation: Int): Boolean =
        this.generation == generation && session != null
    @Synchronized fun hasRenderedFrame(generation: Int): Boolean =
        this.generation == generation && session != null && rendered

    /**
     * Notes [screen], the main screen's output now, for [session], the current one. The session's first
     * observation (made when it starts) sets the baseline; any later change of the attachment count means
     * a screen was attached in between. Once a screen was attached, it stays noted for the session.
     */
    @Synchronized fun observeScreen(generation: Int, session: S, screen: MainScreenOutput) {
        if (this.generation != generation || this.session !== session) return
        val atStart = screenAttachmentsAtStart
        if (screen.attached || (atStart != null && screen.attachments != atStart)) screenShown = true
        if (atStart == null) screenAttachmentsAtStart = screen.attachments
    }

    /** Whether a main-screen surface was attached at some point during the current session. */
    @Synchronized fun screenShown(generation: Int): Boolean =
        this.generation == generation && session != null && screenShown

    /**
     * Whether [session], the current one, has shown that CarPlay works. A rendered frame shows it.
     * While no screen is attached (e.g. CarPlay hidden behind another app) no frame can render, so
     * audio the iPhone sent on the session ([audioReceived]) shows it instead, but only if no screen
     * was attached at any point during the session ([observeScreen], also given [screen] here). A dead
     * session sends neither. Audio is not enough once a screen was attached and no frame rendered:
     * a session that plays sound behind a black screen still fails, even if the user leaves that
     * screen before the check. Once accepted, the session stays accepted.
     */
    @Synchronized fun isUsable(generation: Int, session: S, audioReceived: Boolean, screen: MainScreenOutput): Boolean {
        if (this.generation != generation || this.session !== session) return false
        observeScreen(generation, session, screen)
        if (rendered || (audioReceived && !screenShown)) usable = true
        return usable
    }

    @Synchronized fun end(generation: Int, session: S) {
        if (this.generation != generation || this.session !== session) return
        this.session = null
        authenticated = false
        rendered = false
        usable = false
        screenShown = false
        screenAttachmentsAtStart = null
    }

    @Synchronized fun clear() {
        generation = -1
        session = null
        authenticated = false
        rendered = false
        usable = false
        screenShown = false
        screenAttachmentsAtStart = null
        confirmation = null
    }

    private fun confirmIfReady() {
        if (!authenticated || !rendered) return
        val callback = confirmation ?: return
        confirmation = null
        callback()
    }
}

/**
 * The main screen's output at one moment: whether a surface is attached now, and how many times one has
 * been attached so far (see [com.shilapi.xcertplay.airplay.AirPlayMediaHandler.mainScreenOutputAttachments]).
 */
internal data class MainScreenOutput(val attached: Boolean, val attachments: Long)
