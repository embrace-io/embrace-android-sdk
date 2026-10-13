package io.embrace.android.embracesdk.instrumentation.leaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

/**
 * Regression coverage for [LeakDetector] to make sure we never accidentally capture and store strong references to the objects we
 * are tracking for leaks (ie: make sure the `LeakDetector` is never a source of leaks).
 */
internal class LeakDetectorReachabilityTest {

    private lateinit var detector: LeakDetector

    @Before
    fun setUp() {
        detector = LeakDetector(clock = { 0L }, gcCycleCountReader = { "0" })
    }

    @Test
    fun `an object that is opened but never closed can still be collected`() {
        assertCollectible(openWithoutClosing())
    }

    @Test
    fun `a sentinel holds nothing but its owner, as a primitive`() {
        // a sentinel is a WeakHashMap value keyed by the tracked object - see LeakDetector.Sentinel for why nothing that
        // could reach that object may ever be added to it
        val fields = LeakDetector.Sentinel::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }

        assertEquals(listOf(Int::class.javaPrimitiveType), fields.map { it.type })
    }

    @Test
    fun `an object whose close was refused for a different owner can still be collected`() {
        assertCollectible(openThenRefuseClose())
    }

    @Test
    fun `an object that is tracked and closed can still be collected before any reclaim is observed`() {
        assertCollectible(trackAndClose().target)
    }

    @Test
    fun `an object suspected after outliving one sentinel can still be collected`() {
        assertCollectible(suspect().target)
    }

    @Test
    fun `an object confirmed as a leak suspect can still be collected`() {
        assertCollectible(confirm())
    }

    /**
     * Opens tracking for a new object without ever closing it, and returns only a [WeakReference] to it. The object
     * itself never survives this function's own stack frame, so nothing outside [detector] can be keeping it
     * reachable once this returns.
     */
    private fun openWithoutClosing(): WeakReference<Any> {
        val referent = Any()
        detector.trackOpened(referent, OWNER)
        return WeakReference(referent)
    }

    /**
     * Opens tracking for a new object, then has a different owner try to close it, which leaves its sentinel in place.
     * Same isolation as [openWithoutClosing].
     */
    private fun openThenRefuseClose(): WeakReference<Any> {
        val referent = Any()
        detector.trackOpened(referent, OWNER)
        check(detector.trackClosed(referent, OTHER_OWNER, "token") == null)
        return WeakReference(referent)
    }

    /**
     * Opens and closes tracking for a new object, watched by the first [LeakDetector.TrackedReference] issued for it.
     * Same isolation as [openWithoutClosing] - only the returned reference survives.
     */
    private fun trackAndClose(): LeakDetector.TrackedReference {
        val referent = Any()
        detector.trackOpened(referent, OWNER)
        return checkNotNull(detector.trackClosed(referent, OWNER, "token"))
    }

    /**
     * Simulates the first sentinel reclaim, promoting the tracked object to suspected and returning the second
     * [LeakDetector.TrackedReference] issued for it.
     */
    private fun suspect(): LeakDetector.TrackedReference {
        return checkNotNull(detector.onSentinelReclaimed(trackAndClose()))
    }

    /**
     * Simulates both reclaims needed to confirm the tracked object as a leak suspect, and returns the
     * [LeakDetector.ConfirmedSuspect] now watching it directly.
     */
    private fun confirm(): LeakDetector.ConfirmedSuspect {
        detector.onSentinelReclaimed(suspect())
        return detector.confirmedSuspects().single()
    }

    /**
     * Forces a real collection and asserts [reference] clears - i.e. nothing in [detector] is itself the reason the
     * referent stayed reachable. [System.gc] is only a request, not a guarantee, so this retries a few times rather
     * than trusting a single call.
     */
    private fun assertCollectible(reference: Reference<*>) {
        repeat(GC_ATTEMPTS) {
            if (reference.get() == null) return
            System.gc()
            Thread.sleep(GC_RETRY_DELAY_MS)
        }

        assertNull("the detector appears to be holding a strong reference to the tracked object", reference.get())
    }

    private companion object {
        // any two distinct owners - which instrumentations they belong to doesn't matter here
        val OWNER = TrackingSource.ACTIVITY
        val OTHER_OWNER = TrackingSource.FRAGMENT
        const val GC_ATTEMPTS = 20
        const val GC_RETRY_DELAY_MS = 25L
    }
}
