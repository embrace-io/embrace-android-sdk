package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [action] on the [worker] at most once per burst of requests, so that a run of changes costs
 * one run rather than one per change.
 *
 * The wait is bounded: the run armed by the first request of a burst is never postponed by the
 * requests that join it, and those cost a single volatile read, which is what lets [arm] be called
 * from a hot path. [action] re-derives whatever it writes, so a request that arrives while a run is
 * armed needs nothing kept for it. It runs on the [worker], and this class takes no lock.
 */
internal class DebouncedTrigger(
    private val worker: BackgroundWorker,
    private val delayMs: Long,
    private val action: Runnable,
) {

    /**
     * The run that is on its way, or null if nothing is armed. It is cleared before [action] is
     * handed the work rather than after, so that a request racing a run arms one of its own: the
     * cost is a run that finds nothing to do, rather than a change that never reaches disk.
     */
    private val pending = AtomicReference<PendingRun?>(null)

    /**
     * Arms a run, if one is not armed already. Returns whether a run is on its way: a trigger that
     * cannot reach the [worker] returns false, so the caller can hold on to whatever changed.
     */
    fun arm(): Boolean {
        if (pending.get() != null) {
            return true
        }
        val run = PendingRun()
        if (!pending.compareAndSet(null, run)) {
            return true
        }
        return schedule(run)
    }

    /**
     * Disarms the trigger and runs [action] on the [worker] now. This call does not block, and
     * submits straight to the [worker] so that it still runs once the process is terminating.
     */
    fun flush() {
        disarm()
        runCatching { worker.submit(action) }
    }

    /**
     * Cancels the armed run without running it, for a caller that takes the pending work itself.
     * The caller must take that work after this returns, never before, so that work arriving in
     * between arms a run of its own rather than being stranded.
     */
    fun disarm() {
        pending.getAndSet(null)?.cancel()
    }

    /**
     * Schedules [run]. Returns whether it could be scheduled: if not, nothing will run [action], so
     * the trigger is disarmed to let a later request try again.
     */
    private fun schedule(run: PendingRun): Boolean {
        val scheduled = runCatching {
            worker.schedule<Unit>(Runnable { runAction(run) }, delayMs, TimeUnit.MILLISECONDS)
        }.getOrNull()

        if (scheduled == null) {
            pending.compareAndSet(run, null)
            return false
        }
        // published before the check, so whoever took this run over cancels the trigger if this
        // thread had not attached it yet
        run.trigger = scheduled
        if (pending.get() !== run) {
            scheduled.cancel(false)
        }
        return true
    }

    private fun runAction(run: PendingRun) {
        pending.compareAndSet(run, null)
        action.run()
    }

    /** One armed run, created before its trigger exists. */
    private class PendingRun {

        @Volatile
        var trigger: Future<*>? = null

        fun cancel() {
            trigger?.cancel(false)
        }
    }
}
