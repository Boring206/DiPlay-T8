package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.hud.BydAmbientLampHold
import com.shilapi.xcertplay.hud.BydAmbientLightPolicy
import java.util.concurrent.CompletableFuture

/** Serialized lamp lifecycle used by the real controller and fake-client tests. No audio or timer thread here. */
internal class AmbientLampSession(
    private val factory: () -> Client,
    private val dispatch: (() -> Unit) -> Unit,
    private val now: () -> Long,
    private val log: (String) -> Unit = {},
    private val applied: (Triple<Int, Int, Int>) -> Unit = {},
    /** One line for the diagnostic report when lamp writes pause or resume. */
    private val diagnostic: (String) -> Unit = {},
) {
    /** What the worker did with one apply; [held] when it wrote nothing because the lamp read [lamp] (#1332). */
    data class Outcome(val held: Boolean, val lamp: BydAmbientLightPolicy.Snapshot? = null) {
        companion object { val WRITTEN = Outcome(held = false) }
    }

    interface Client {
        fun unhealthy(): Boolean
        fun begin(seed: BydAmbientLightPolicy.Snapshot?): CompletableFuture<BydAmbientLightPolicy.Snapshot>
        fun apply(desired: Triple<Int, Int, Int>): CompletableFuture<Outcome>
        /** The lamp as read now becomes the worker's baseline, which ends a hold. */
        fun rebase(): CompletableFuture<Unit>
        fun stop(): CompletableFuture<Unit>
        /** Called after stop completes; must close stdin/transport before returning control to future retries. */
        fun close()
    }
    private var enabled = false
    private var client: Client? = null
    private var ready = false
    private var applying = false
    private var stopping = false
    private var failures = 0
    private var retryAt = 0L
    private var original: BydAmbientLightPolicy.Snapshot? = null
    private var restoreUnconfirmed = false
    private var lastApplied: Triple<Int, Int, Int>? = null
    private var heldSince: Long? = null
    /** The value last held, and when it may be asked for again (#1332). */
    private var heldDesired: Triple<Int, Int, Int>? = null
    private var heldRetryAt = 0L
    /** Counts rebases, so a held reply to an apply sent before one does not start a new hold. */
    private var rebases = 0L
    private val cadence = AmbientMusicApplyCadence()

    /** Called on the owning controller executor; an active worker need not be probed again. */
    fun isReady(): Boolean = ready && !stopping && client?.unhealthy() == false

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            if (client == null && !stopping && !restoreUnconfirmed) {
                original = null; failures = 0; retryAt = now()
            }
            stopOwned(false)
        } else {
            // An unconfirmed previous stop retains its snapshot and its safety deadline.
            if (!stopping && original == null && failures == 0) retryAt = now()
        }
    }

    /** Called before desired-value deduplication, even if music stays on one color indefinitely. */
    fun update(desired: Triple<Int, Int, Int>) {
        if (!enabled || stopping) return
        val owned = client
        if (owned != null && owned.unhealthy()) { failOwned("inactive"); return }
        if (owned == null) {
            if (now() < retryAt) return
            val opening = try { factory() } catch (_: Throwable) { scheduleRetry(false); return }
            client = opening
            ready = false
            log("lamp begin retry=$failures seeded=${original != null}")
            val started = try { opening.begin(original) } catch (error: Throwable) {
                CompletableFuture<BydAmbientLightPolicy.Snapshot>().also { it.completeExceptionally(error) }
            }
            started.whenComplete { snapshot, error -> dispatch {
                if (client !== opening || stopping) return@dispatch
                if (error != null || snapshot == null || !BydAmbientLightPolicy.validSnapshot(snapshot)) {
                    failOwned("begin")
                } else {
                    if (original == null) original = snapshot
                    ready = true
                    // Reset backoff only after an actual apply succeeds, not a begin/apply failure loop.
                    log("lamp session ready")
                }
            } }
            return
        }
        if (!ready || applying || lastApplied == desired) return
        // While held, the worker is asked again for the same value only every RETRY_MILLIS; a new value goes at once.
        if (heldSince != null && desired == heldDesired && now() < heldRetryAt) return
        if (!cadence.trySubmit(now())) return
        applying = true
        val sentAt = rebases
        val future = try { owned.apply(desired) } catch (error: Throwable) {
            CompletableFuture<Outcome>().also { it.completeExceptionally(error) }
        }
        future.whenComplete { outcome, error -> dispatch {
            if (client !== owned || stopping) return@dispatch
            applying = false
            if (error == null && outcome?.held == true) {
                // The worker was rebased after this apply was read; the next tick asks again at once.
                if (sentAt != rebases) return@dispatch
                // lastApplied stays, so a later tick asks again and the worker reads the lamp again first.
                heldDesired = desired
                heldRetryAt = now() + BydAmbientLampHold.RETRY_MILLIS
                if (heldSince == null) {
                    heldSince = now()
                    diagnostic("Ambient: lamp writes paused, lamp changed outside DiPlay" +
                        " lamp=${outcome.lamp?.let(::describe) ?: "unknown"} holdOffMs=${BydAmbientLampHold.HOLD_MILLIS}")
                }
            } else if (error == null) {
                heldSince?.let { diagnostic("Ambient: lamp writes resumed after ${now() - it} ms") }
                heldSince = null; heldDesired = null
                lastApplied = desired
                failures = 0
                applied(desired)
            } else if (!ambientApplyCancelled(error)) failOwned("apply")
        } }
    }

    /**
     * Ends a hold: the worker takes the lamp as read now as its baseline, so the next apply writes. Called for a
     * DiPlay Settings change and a new CarPlay session; a lamp that is not ready yet gets a fresh baseline at begin.
     */
    fun rebase(reason: String) {
        val owned = client
        if (!enabled || stopping || !ready || owned == null) return
        heldSince?.let { diagnostic("Ambient: lamp hold cleared ($reason) after ${now() - it} ms") }
        heldSince = null; heldDesired = null; rebases++
        val future = try { owned.rebase() } catch (error: Throwable) {
            CompletableFuture<Unit>().also { it.completeExceptionally(error) }
        }
        future.whenComplete { _, error -> dispatch {
            if (client !== owned || stopping || error == null || ambientApplyCancelled(error)) return@dispatch
            failOwned("rebase")
        } }
    }

    fun recover() { if (enabled) failOwned("controller") }

    private fun failOwned(reason: String) {
        log("lamp $reason failed; recovery scheduled")
        stopOwned(true)
    }

    private fun scheduleRetry(uncertainStop: Boolean) {
        val backoff = when (failures.coerceAtMost(3)) { 0 -> 5_000L; 1 -> 10_000L; 2 -> 20_000L; else -> 30_000L }
        failures = (failures + 1).coerceAtMost(4)
        val delay = maxOf(backoff, if (uncertainStop) BydAmbientLightPolicy.LEASE_MILLIS + 1_000 else 0)
        retryAt = now() + delay
        log("lamp retry in ${delay}ms enabled=$enabled restorePending=$uncertainStop")
    }

    private fun describe(s: BydAmbientLightPolicy.Snapshot) =
        "front ${s.frontColor}/${s.frontBrightness} back ${s.backColor}/${s.backBrightness} area ${s.area}"

    private fun stopOwned(recover: Boolean) {
        if (stopping) return
        val old = client ?: return
        stopping = true
        client = null // All old begin/apply callbacks immediately lose ownership.
        ready = false; applying = false; lastApplied = null; cadence.reset()
        // The stop restores the car's original lamp values, which ends the hold.
        heldSince?.let { diagnostic("Ambient: lamp hold ended after ${now() - it} ms, lamp session stopping") }
        heldSince = null; heldDesired = null
        val stopped = try { old.stop() } catch (error: Throwable) {
            CompletableFuture<Unit>().also { it.completeExceptionally(error) }
        }
        stopped.whenComplete { _, error -> dispatch {
            var uncertain = error != null
            try { old.close() } catch (_: Throwable) { uncertain = true }
            stopping = false
            restoreUnconfirmed = uncertain
            if (recover || uncertain) scheduleRetry(uncertain)
            else { failures = 0; retryAt = now() }
            if (!enabled && !uncertain) original = null
            // Disabled means restoration only. Never start a new worker/apply until re-enabled.
            log("lamp stop complete confirmed=${!uncertain} enabled=$enabled")
        } }
    }
}
