package io.embrace.android.embracesdk.instrumentation.leaks

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LeakDetectorTest {

    private class ReportedLeak(
        val referent: Any,
        val trackedAtMs: Long,
        val token: Any?,
    )

    private var now: Long = 1000L
    private val reported = mutableListOf<ReportedLeak>()

    private lateinit var detector: LeakDetector

    @Before
    fun setUp() {
        now = 1000L
        reported.clear()
        detector = LeakDetector(clock = { now }) { referent, trackedAtMs, token ->
            reported.add(ReportedLeak(referent, trackedAtMs, token))
        }
    }

    @After
    fun tearDown() {
        // wait for the thread to exit so that it cannot be observed by the next test
        val thread = detectorThread()
        detector.stop()
        thread?.join(THREAD_EXIT_TIMEOUT_MS)
    }

    @Test
    fun `an object that outlives a second sentinel is reported`() {
        // held strongly for the duration of the test so that it cannot be collected
        val leaked = Any()
        val token = "Hello!"

        detector.trackOpened(leaked)
        now = 5000L
        val ref = checkNotNull(detector.trackClosed(leaked, token))

        now = 9000L
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        assertTrue("outliving one sentinel only makes it a suspect", reported.isEmpty())

        assertNull("reporting ends the chain", detector.onSentinelReclaimed(confirmation))

        val leak = reported.single()
        assertSame(leaked, leak.referent)
        assertEquals(5000L, leak.trackedAtMs)
        assertSame(token, leak.token)
    }

    @Test
    fun `an object released before its second sentinel is reclaimed is not reported`() {
        val brieflyHeld = Any()
        detector.trackOpened(brieflyHeld)
        val ref = checkNotNull(detector.trackClosed(brieflyHeld))

        // something was still holding it as the lifecycle ended, so it outlives the first sentinel
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))

        // that hold is released, so the collection reclaiming the second sentinel takes it too
        confirmation.target.clear()

        assertNull(detector.onSentinelReclaimed(confirmation))
        assertTrue("a hold that was released is not a leak", reported.isEmpty())
    }

    @Test
    fun `the second sentinel tracks the same object as the first`() {
        val leaked = Any()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked, "token"))

        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))

        assertSame("the weak reference is carried over rather than reallocated", ref.target, confirmation.target)
        assertEquals(ref.trackedAtMs, confirmation.trackedAtMs)
        assertSame(ref.token, confirmation.token)
    }

    @Test
    fun `an object collected alongside its sentinel is not reported`() {
        val collected = Any()
        detector.trackOpened(collected)
        val ref = checkNotNull(detector.trackClosed(collected))

        // the collection that reclaimed the sentinel reclaimed the tracked object too
        ref.target.clear()

        assertNull("a collected object is never suspected", detector.onSentinelReclaimed(ref))
        assertTrue("an object that was collected is not a leak", reported.isEmpty())
    }

    @Test
    fun `an object with no sentinel is not tracked when its lifecycle ends`() {
        val neverOpened = Any()

        assertNull("without a sentinel there is nothing to compare against", detector.trackClosed(neverOpened))
    }

    @Test
    fun `opening the same object twice does not create a second sentinel`() {
        val reopened = Any()

        detector.trackOpened(reopened)
        detector.trackOpened(reopened)

        assertNotNull(detector.trackClosed(reopened))
        assertNull("the second open must not have added a sentinel of its own", detector.trackClosed(reopened))
    }

    @Test
    fun `each tracked object is reported independently of the others`() {
        val first = Any()
        val second = Any()

        detector.trackOpened(first)
        detector.trackOpened(second)
        val firstRef = checkNotNull(detector.trackClosed(first))
        val secondRef = checkNotNull(detector.trackClosed(second))

        reportAfterSecondSentinel(firstRef)
        assertEquals(listOf(first), reported.map { it.referent })

        reportAfterSecondSentinel(secondRef)
        assertEquals(listOf(first, second), reported.map { it.referent })
    }

    @Test
    fun `a leak is only reported once`() {
        val leaked = Any()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked))

        val confirmation = reportAfterSecondSentinel(ref)
        assertEquals(1, reported.size)

        repeat(5) {
            detector.onSentinelReclaimed(ref)
            detector.onSentinelReclaimed(confirmation)
        }

        assertEquals(1, reported.size)
    }

    @Test
    fun `nothing tracked is reported after the detector stops`() {
        val leaked = Any()
        detector.start()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked))

        detector.stop()
        detector.onSentinelReclaimed(ref)

        assertTrue(reported.isEmpty())
    }

    /**
     * Drives both reclamations the detector thread would otherwise drive, returning the confirmation reference.
     */
    private fun reportAfterSecondSentinel(ref: LeakDetector.TrackedReference): LeakDetector.TrackedReference {
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        detector.onSentinelReclaimed(confirmation)
        return confirmation
    }

    private fun detectorThread(): Thread? =
        Thread.getAllStackTraces().keys.firstOrNull { it.name == LeakDetector.THREAD_NAME }

    private companion object {
        const val THREAD_EXIT_TIMEOUT_MS = 5000L
    }
}
