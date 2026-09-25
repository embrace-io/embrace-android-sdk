package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.concurrency.runActionsConcurrently
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class LeakDetectorTest {

    private var now: Long = 1000L
    private var gcCycleCount: Long = 0L

    private lateinit var detector: LeakDetector

    @Before
    fun setUp() {
        now = 1000L
        gcCycleCount = 0L
        detector = LeakDetector(clock = { now }, gcCycleCountReader = { gcCycleCount.toString() })
    }

    @After
    fun tearDown() {
        // wait for the thread to exit so that it cannot be observed by the next test
        val thread = detectorThread()
        detector.stop()
        thread?.join(THREAD_EXIT_TIMEOUT_MS)
    }

    @Test
    fun `an object that outlives a second sentinel becomes a tracked suspect`() {
        // held strongly for the duration of the test so that it cannot be collected
        val leaked = Any()
        val token = "Hello!"

        detector.trackOpened(leaked)
        now = 5000L
        val ref = checkNotNull(detector.trackClosed(leaked, token))

        now = 9000L
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        assertTrue("outliving one sentinel only makes it a suspect", detector.suspects().isEmpty())

        gcCycleCount = 3L
        assertNull("confirming ends the sentinel chain", detector.onSentinelReclaimed(confirmation))

        val suspect = detector.suspects().single()
        assertEquals(5000L, suspect.trackedAtMs)
        assertSame(token, suspect.token)
        assertEquals("surviving the sentinel that confirmed it already counts as one cycle survived", 1L, suspect.cyclesSurvived)
        assertEquals(
            "read fresh from the still-live object rather than captured at confirmation",
            leaked.javaClass.name,
            suspect.className,
        )
        assertEquals(System.identityHashCode(leaked), suspect.id)

        gcCycleCount = 7L
        assertEquals(
            "4 more cycles have passed since confirmation, on top of the 1 already counted",
            5L,
            detector.suspects().single().cyclesSurvived,
        )
    }

    @Test
    fun `id identifies the referent and stays stable across repeated snapshots`() {
        val first = Any()
        val second = Any()

        detector.trackOpened(first)
        detector.trackOpened(second)
        val firstRef = checkNotNull(detector.trackClosed(first, "first"))
        val secondRef = checkNotNull(detector.trackClosed(second, "second"))
        confirmAfterSecondSentinel(firstRef)
        confirmAfterSecondSentinel(secondRef)

        val snapshots = detector.suspects().associateBy { it.token }
        assertEquals(System.identityHashCode(first), snapshots.getValue("first").id)
        assertEquals(System.identityHashCode(second), snapshots.getValue("second").id)

        // stable across repeated snapshots, not re-randomized per call
        assertEquals(snapshots.getValue("first").id, detector.suspects().single { it.token == "first" }.id)
    }

    @Test
    fun `an object released before its second sentinel is reclaimed does not become a suspect`() {
        val brieflyHeld = Any()
        detector.trackOpened(brieflyHeld)
        val ref = checkNotNull(detector.trackClosed(brieflyHeld))

        // something was still holding it as the lifecycle ended, so it outlives the first sentinel
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))

        // that hold is released, so the collection reclaiming the second sentinel takes it too
        confirmation.target.clear()

        assertNull(detector.onSentinelReclaimed(confirmation))
        assertTrue("a hold that was released is not a leak", detector.suspects().isEmpty())
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
    fun `an object collected alongside its sentinel never becomes a suspect`() {
        val collected = Any()
        detector.trackOpened(collected)
        val ref = checkNotNull(detector.trackClosed(collected))

        // the collection that reclaimed the sentinel reclaimed the tracked object too
        ref.target.clear()

        assertNull("a collected object is never suspected", detector.onSentinelReclaimed(ref))
        assertTrue("an object that was collected is not a leak", detector.suspects().isEmpty())
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
    fun `each tracked object becomes a suspect independently of the others`() {
        val first = Any()
        val second = Any()

        detector.trackOpened(first)
        detector.trackOpened(second)
        val firstRef = checkNotNull(detector.trackClosed(first, "first"))
        val secondRef = checkNotNull(detector.trackClosed(second, "second"))

        confirmAfterSecondSentinel(firstRef)
        assertEquals(listOf("first"), detector.suspects().map { it.token })

        confirmAfterSecondSentinel(secondRef)
        assertEquals(setOf("first", "second"), detector.suspects().map { it.token }.toSet())
    }

    @Test
    fun `a confirmed suspect is only added once`() {
        val leaked = Any()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked))

        val confirmation = confirmAfterSecondSentinel(ref)
        assertEquals(1, detector.suspects().size)

        repeat(5) {
            detector.onSentinelReclaimed(ref)
            detector.onSentinelReclaimed(confirmation)
        }

        assertEquals(1, detector.suspects().size)
    }

    @Test
    fun `a suspect that is actually collected is discarded from the dataset`() {
        val leaked = Any()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked))
        confirmAfterSecondSentinel(ref)

        assertEquals(1, detector.suspects().size)

        val suspect = detector.confirmedSuspects().single()
        detector.onSuspectCollected(suspect)

        assertTrue("an object that was actually collected is not a leak", detector.suspects().isEmpty())
    }

    @Test
    fun `a suspect that has already cleared is omitted from a snapshot`() {
        val leaked = Any()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked))
        confirmAfterSecondSentinel(ref)

        // simulates the object actually being collected before suspects() reads it, without waiting for a real one or
        // for onSuspectCollected to catch up via the queue
        detector.confirmedSuspects().single().clear()

        assertTrue("a suspect with nothing left to read is not reported", detector.suspects().isEmpty())
    }

    @Test
    fun `nothing is tracked as a suspect after the detector stops`() {
        val leaked = Any()
        detector.start()
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked))

        detector.stop()
        detector.onSentinelReclaimed(ref)

        assertTrue(detector.suspects().isEmpty())
    }

    @Test
    fun `concurrent trackClosed and onSentinelReclaimed keep watched consistent`() {
        val threadCount = 8
        val opsPerThread = 500
        val total = threadCount * opsPerThread
        // held strongly for the duration of the test so that nothing tracked can be collected for real
        val referents = ConcurrentLinkedQueue<Any>()
        val closedRefs = LinkedBlockingQueue<LeakDetector.TrackedReference>()
        val reclaimed = mutableListOf<LeakDetector.TrackedReference>()
        var collectedCount = 0

        val workers = List(threadCount) { threadIndex ->
            {
                repeat(opsPerThread) { op ->
                    val referent = Any()
                    referents.add(referent)
                    detector.trackOpened(referent)
                    val ref = checkNotNull(detector.trackClosed(referent, threadIndex * opsPerThread + op))
                    if (detector.trackClosed(referent) != null) {
                        error("a closed lifecycle must not be tracked twice")
                    }
                    closedRefs.add(ref)
                }
            }
        }
        // drains closed references while the workers are still producing them, so reclaims overlap closes
        val reclaimer: () -> Unit = {
            repeat(total) {
                val ref = checkNotNull(closedRefs.poll(10, TimeUnit.SECONDS))
                if ((ref.token as Int) % 4 == 0) {
                    // the collection that reclaimed the sentinel reclaimed the tracked object too
                    ref.target.clear()
                    collectedCount++
                    if (detector.onSentinelReclaimed(ref) != null) {
                        error("a collected object must not be re-watched")
                    }
                } else {
                    val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
                    if (detector.onSentinelReclaimed(confirmation) != null) {
                        error("confirming must end the sentinel chain")
                    }
                }
                reclaimed.add(ref)
            }
        }

        runActionsConcurrently(workers + reclaimer)

        assertEquals(total, referents.size)
        assertEquals(total, reclaimed.size)
        assertEquals(total - collectedCount, detector.suspects().size)
        assertEquals(
            (0 until total).filter { it % 4 != 0 }.toSet(),
            detector.suspects().map { it.token }.toSet(),
        )
        assertTrue(
            "every reference has already been handled, so none may still be watched",
            reclaimed.all { detector.onSentinelReclaimed(it) == null },
        )
    }

    /**
     * Drives both reclamations the detector thread would otherwise drive, confirming the suspect. Returns the confirmation
     * reference that was passed to the second reclamation.
     */
    private fun confirmAfterSecondSentinel(ref: LeakDetector.TrackedReference): LeakDetector.TrackedReference {
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
