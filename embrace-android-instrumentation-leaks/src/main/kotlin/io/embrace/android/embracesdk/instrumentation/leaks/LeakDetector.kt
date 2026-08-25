package io.embrace.android.embracesdk.instrumentation.leaks

import android.os.Build
import android.os.Debug
import io.embrace.android.embracesdk.internal.clock.Clock
import java.lang.ref.PhantomReference
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Detects objects that outlive the end of their own lifecycle, and tracks how many GC cycles they go on to survive.
 *
 * Each tracked object is paired with a [Sentinel] allocated when its lifecycle begins and released when that lifecycle ends.
 * The two should be reclaimed by the same collection, so a collection that reclaims the sentinel could have reclaimed the
 * tracked object as well. Collections may be region-targeted, so the number of collections an object survives does not
 * establish whether it could have been collected.
 *
 * An object still reachable at that point is suspected rather than confirmed. It is paired with a new [Sentinel] and only
 * confirmed as a leak if it outlives that one too, which discards objects that something else was briefly holding as the
 * lifecycle ended, such as the framework finishing its own teardown.
 *
 * A confirmed leak is not reported once and forgotten - it is watched, via a [ConfirmedSuspect] registered directly on it,
 * for as long as it remains reachable. [suspects] reports how many GC cycles each one has survived since being confirmed,
 * using [currentGcCycleCount] - a single shared reading, so that a suspect only needs to remember its own cycle count at
 * confirmation rather than being ticked on every cycle itself. A suspect that eventually gets collected is simply
 * discarded, since a [ConfirmedSuspect] is watched by the same queue as everything else here.
 *
 * The caller decides what the start and end of a lifecycle mean. Use [trackOpened] and [trackClosed] to mark them, and [start]
 * and [stop] to control the thread that drains the queue.
 */
internal class LeakDetector(
    private val clock: Clock,
    // guarded here (rather than relying on LeakDetectionInstrumentationProvider never constructing this class below API
    // 23) so that lint's own NewApi check, which can't trace that guard through a lazily-invoked factory in another
    // file, can see it too
    private val gcCycleCountReader: () -> String? = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Debug.getRuntimeStat(ART_GC_COUNT_STAT)
        } else {
            null
        }
    },
) {
    private val queue = ReferenceQueue<Any>()

    /**
     * The last cycle count successfully read from [gcCycleCountReader]. Falls back to this if a read returns null or
     * something unparsable, rather than reporting zero, so that a single transient failure cannot make the count appear
     * to go backwards. Starts at zero until the first successful read.
     */
    @Volatile
    private var lastKnownGcCycleCount: Long = 0L

    /**
     * The number of GC cycles the runtime has performed so far, per [gcCycleCountReader]. Only meaningful as a delta
     * between two reads - see [suspects] and [onSentinelReclaimed].
     */
    private fun currentGcCycleCount(): Long {
        val value = gcCycleCountReader()?.toLongOrNull() ?: return lastKnownGcCycleCount
        lastKnownGcCycleCount = value
        return value
    }

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

    /**
     * Confirmed suspects not yet actually collected. Held strongly for the same reason as [watched]: each entry's
     * [ConfirmedSuspect] must stay reachable itself in order to eventually be enqueued.
     */
    private val trackedSuspects = HashSet<ConfirmedSuspect>()

    private val running = AtomicBoolean(false)

    private var detectorThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        val worker = thread(start = false, name = THREAD_NAME) {
            try {
                while (running.get()) {
                    dispatch(queue.remove())

                    // drain whatever else is already available before blocking again, rather than parking and waking
                    // once per item
                    var next = queue.poll()
                    while (next != null) {
                        dispatch(next)
                        next = queue.poll()
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
     * have reclaimed [referent], which would confirm [referent] as leaked when it is not.
     *
     * Calling this more than once for the same object has no effect after the first call.
     */
    fun trackOpened(referent: Any) {
        openSentinel(referent)
    }

    /**
     * Release the sentinel allocated for [referent] by [trackOpened] and watch for its reclamation. Call this as the lifecycle
     * of [referent] ends. [token] is carried through to [suspects] if [referent] is confirmed as a leak, and must not hold a
     * reference to [referent], following the same rules as a `WeakHashMap` value.
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
     * Pair the object tracked by [ref] with a new [Sentinel] if it is still reachable, or confirm it as a probable leak if it
     * has already outlived one. Invoked on the detector thread as the queue delivers reclaimed sentinels, and visible so that
     * tests can drive reclamation without relying on a real collection.
     *
     * A collection clears weak references before it enqueues phantom references, so the reachability of the tracked object is
     * already settled by the time this is called.
     *
     * Returns the reference now watching the new sentinel. Returns null if the tracked object was confirmed as a suspect, was
     * collected, or if [ref] was no longer being watched.
     */
    fun onSentinelReclaimed(ref: TrackedReference): TrackedReference? {
        if (!stopWatching(ref)) {
            // already handled, or dropped by stop()
            return null
        }

        ref.clear()

        val referent = ref.target.get() ?: return null

        if (ref.suspected) {
            val suspect = ConfirmedSuspect(
                referent,
                queue,
                ref.trackedAtMs,
                ref.token,
                currentGcCycleCount(),
            )

            addSuspect(suspect)
            return null
        }

        val confirmation = TrackedReference(Sentinel(), queue, ref.target, ref.trackedAtMs, ref.token, suspected = true)
        watch(confirmation)
        return confirmation
    }

    /**
     * Invoked on the detector thread when a [ConfirmedSuspect] is dequeued, meaning the object it watches has actually been
     * collected. There is nothing to report - it was never a persistent leak - so it is simply discarded. Visible so that
     * tests can drive collection without relying on a real one.
     */
    fun onSuspectCollected(suspect: ConfirmedSuspect) {
        synchronized(trackedSuspects) {
            trackedSuspects.remove(suspect)
        }
    }

    /**
     * A snapshot of every confirmed suspect still reachable, safe to hand outside the detector: it never carries the tracked
     * object itself, only what [trackClosed] was given, its class name read fresh from the still-live object, and how many
     * GC cycles it has survived since being confirmed.
     */
    fun suspects(): List<LeakSnapshot> {
        val cycleCount = currentGcCycleCount()
        return synchronized(trackedSuspects) {
            trackedSuspects.mapNotNull { suspect ->
                val referent = suspect.get() ?: return@mapNotNull null
                // The suspectedAtCycle is captured after the first GC cycle (when a TrackedReference becomes a ConfirmedSuspect).
                // So we +1 here so that cycle is represented in the reporting (instead of reporting a confusing 0 value).
                val cyclesSurvived = cycleCount - suspect.suspectedAtCycle + 1
                LeakSnapshot(suspect.trackedAtMs, suspect.token, cyclesSurvived, referent.javaClass.name)
            }
        }
    }

    /**
     * The [ConfirmedSuspect] entries backing [suspects], visible so that tests can drive [onSuspectCollected] for a real
     * entry without waiting on an actual collection.
     */
    fun confirmedSuspects(): Set<ConfirmedSuspect> =
        synchronized(trackedSuspects) {
            trackedSuspects.toSet()
        }

    /*
     * All access to sentinels, watched and trackedSuspects goes through the functions below. All three are shared: tracking
     * happens on the thread that begins or ends a lifecycle, and reclamation is delivered on the detector thread.
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

    private fun addSuspect(suspect: ConfirmedSuspect) {
        synchronized(trackedSuspects) {
            trackedSuspects.add(suspect)
        }
    }

    private fun clearTracking() {
        synchronized(sentinels) {
            sentinels.clear()
        }

        synchronized(watched) {
            watched.clear()
        }

        synchronized(trackedSuspects) {
            trackedSuspects.clear()
        }
    }

    private fun dispatch(reclaimed: Reference<*>) {
        when (reclaimed) {
            is TrackedReference -> onSentinelReclaimed(reclaimed)
            is ConfirmedSuspect -> onSuspectCollected(reclaimed)
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
     * Watches the [Sentinel] belonging to a tracked object, and carries what is needed to confirm or re-watch it. The tracked
     * object is held weakly so that watching it cannot keep it in memory.
     *
     * [suspected] marks the second sentinel a tracked object is given, after it has already outlived one. Outliving that one
     * as well is what confirms it as a leak, so this reference adds the object to [trackedSuspects] rather than pairing it up
     * again.
     */
    internal class TrackedReference(
        sentinel: Sentinel,
        queue: ReferenceQueue<Any>,
        val target: WeakReference<Any>,
        val trackedAtMs: Long,
        val token: Any?,
        val suspected: Boolean = false,
    ) : PhantomReference<Sentinel>(sentinel, queue)

    /**
     * Watches a confirmed suspect directly, rather than through a paired [Sentinel]: by this point a collection capable of
     * reclaiming the object has already been proven twice over, so all that is left to know is exactly when it eventually
     * stops being reachable at all - which weak reachability already answers. A [PhantomReference] would only fire after
     * finalization as well, and would hold the referent's memory alive until explicitly cleared; neither is needed here,
     * since nothing but bookkeeping happens once a suspect is gone.
     *
     * [get] doubles as the live read [suspects] uses for the class name, since unlike a [PhantomReference] a
     * [WeakReference] hands the referent back for as long as it is still reachable.
     */
    internal class ConfirmedSuspect(
        referent: Any,
        queue: ReferenceQueue<Any>,
        val trackedAtMs: Long,
        val token: Any?,
        val suspectedAtCycle: Long,
    ) : WeakReference<Any>(referent, queue)

    /**
     * One entry of [suspects]: everything needed to report a confirmed suspect, read fresh at snapshot time rather than
     * retained by the [ConfirmedSuspect] itself, and nothing that could retain the object it describes.
     */
    internal data class LeakSnapshot(
        val trackedAtMs: Long,
        val token: Any?,
        val cyclesSurvived: Long,
        val className: String,
    )

    internal companion object {
        const val THREAD_NAME = "emb-leak-detector"
        const val ART_GC_COUNT_STAT = "art.gc.gc-count"
    }
}
