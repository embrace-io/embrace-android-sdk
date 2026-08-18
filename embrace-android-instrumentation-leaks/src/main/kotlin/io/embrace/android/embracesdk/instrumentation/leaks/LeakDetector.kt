package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.internal.clock.Clock
import java.lang.ref.PhantomReference
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Detects objects that outlive the end of their own lifecycle.
 *
 * Each tracked object is paired with a [Sentinel] allocated when its lifecycle begins and released when that lifecycle ends.
 * The two should be reclaimed by the same collection, so a collection that reclaims the sentinel could have reclaimed the
 * tracked object as well, and anything still reachable at that point is reported to [listener] as a probable leak. Collections
 * may be region-targeted, so the number of collections an object survives does not establish whether it could have been
 * collected.
 *
 * The caller decides what the start and end of a lifecycle mean. Use [trackOpened] and [trackClosed] to mark them, and [start]
 * and [stop] to control the thread that reports leaks.
 */
internal class LeakDetector(
    private val clock: Clock,
    private val listener: LeakListener,
) {
    private val queue = ReferenceQueue<Sentinel>()

    /**
     * Sentinels for lifecycles that have begun but not yet ended. Keys are held weakly so that an object collected before its
     * lifecycle ends drops out along with its sentinel.
     */
    private val sentinels = WeakHashMap<Any, Sentinel>()

    /**
     * References waiting for their sentinel to be reclaimed. Held strongly because a [Reference] that becomes unreachable is
     * never enqueued.
     */
    private val watched = HashSet<TrackedReference>()

    private val running = AtomicBoolean(false)

    private var detectorThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        val worker = thread(start = false, name = THREAD_NAME) {
            try {
                while (running.get()) {
                    val reclaimed = queue.remove()
                    if (reclaimed is TrackedReference) {
                        onSentinelReclaimed(reclaimed)
                    }
                }
            } catch (_: InterruptedException) {
                // stop() interrupts the blocking queue.remove() so the thread exits without waiting for the next collection
            }

            clearTracking()
        }

        detectorThread = worker
        worker.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        clearTracking()
        detectorThread?.interrupt()
        detectorThread = null
    }

    /**
     * Start tracking [referent] by allocating its sentinel. Call this as the lifecycle of [referent] begins, and as early in
     * that lifecycle as possible. A sentinel allocated well after [referent] may be reclaimed by collections that could not
     * have reclaimed [referent], which would report [referent] as leaked when it is not.
     *
     * Calling this more than once for the same object has no effect after the first call.
     */
    fun trackOpened(referent: Any) {
        openSentinel(referent)
    }

    /**
     * Release the sentinel allocated for [referent] by [trackOpened] and watch for its reclamation. Call this as the lifecycle
     * of [referent] ends. [token] is delivered to [LeakListener.onLeakDetected] if [referent] is reported as leaked, and must
     * not hold a reference to [referent], following the same rules as a `WeakHashMap` value.
     *
     * Returns the reference now being watched. Returns null if no sentinel is held for [referent], either because
     * [trackOpened] was not called for it or because its lifecycle has already ended, in which case it is not tracked.
     */
    fun trackClosed(referent: Any, token: Any? = null): TrackedReference? {
        val sentinel = releaseSentinel(referent) ?: return null

        val ref = TrackedReference(sentinel, queue, WeakReference(referent), clock.now(), token)
        watch(ref)
        return ref
    }

    /**
     * Report the object tracked by [ref] as a probable leak if it is still reachable. Invoked on the detector thread as the
     * queue delivers reclaimed sentinels, and visible so that tests can drive reclamation without relying on a real collection.
     *
     * A collection clears weak references before it enqueues phantom references, so the reachability of the tracked object is
     * already settled by the time this is called.
     */
    fun onSentinelReclaimed(ref: TrackedReference) {
        if (!stopWatching(ref)) {
            // already handled, or dropped by stop()
            return
        }

        val referent = ref.target.get()
        if (referent != null) {
            listener.onLeakDetected(referent, ref.trackedAtMs, ref.token)
        }

        ref.clear()
    }

    /*
     * All access to sentinels and watched goes through the functions below. Both are shared: tracking happens on the thread
     * that begins or ends a lifecycle, and reclamation is delivered on the detector thread.
     */

    private fun openSentinel(referent: Any) {
        synchronized(sentinels) {
            if (!sentinels.containsKey(referent)) {
                sentinels[referent] = Sentinel()
            }
        }
    }

    private fun releaseSentinel(referent: Any): Sentinel? = synchronized(sentinels) {
        sentinels.remove(referent)
    }

    private fun watch(ref: TrackedReference) {
        synchronized(watched) {
            watched.add(ref)
        }
    }

    private fun stopWatching(ref: TrackedReference): Boolean =
        synchronized(watched) {
            watched.remove(ref)
        }

    private fun clearTracking() {
        synchronized(sentinels) {
            sentinels.clear()
        }

        synchronized(watched) {
            watched.clear()
        }
    }

    /**
     * Allocated alongside a tracked object and released at the same time, so that its reclamation indicates that a collection
     * capable of reclaiming that object has run. Holds no state.
     *
     * This is a named type rather than an [Any] so that it can be identified in a heap dump, where anonymous objects retained
     * by the SDK would look like a bug in it.
     */
    internal class Sentinel

    /**
     * Watches the [Sentinel] belonging to a tracked object, and carries what [LeakListener] needs to report it. The tracked
     * object is held weakly so that watching it cannot keep it in memory.
     */
    internal class TrackedReference(
        sentinel: Sentinel,
        queue: ReferenceQueue<Sentinel>,
        val target: WeakReference<Any>,
        val trackedAtMs: Long,
        val token: Any?,
    ) : PhantomReference<Sentinel>(sentinel, queue)

    internal companion object {
        const val THREAD_NAME = "emb-leak-detector"
    }
}
