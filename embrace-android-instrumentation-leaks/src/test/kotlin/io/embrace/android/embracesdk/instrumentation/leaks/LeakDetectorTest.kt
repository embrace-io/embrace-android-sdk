package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.concurrency.runActionsConcurrently
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
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
import java.util.concurrent.atomic.AtomicBoolean

internal class LeakDetectorTest {

    private var now: Long = 1000L
    private var gcCycleCount: Long = 0L

    private lateinit var detector: LeakDetector

    @Before
    fun setUp() {
        now = 1000L
        // a real counter has counted collections by the time anything is confirmed, since confirming takes two
        gcCycleCount = 1L
        detector = LeakDetector(
            clock = { now },
            errorHandler = FakeErrorHandler(),
            versionChecker = BuildVersionChecker,
            gcCycleCountReader = { gcCycleCount.toString() },
        )
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

        detector.trackOpened(leaked, OWNER)
        now = 5000L
        val ref = checkNotNull(detector.trackClosed(leaked, OWNER, token))

        now = 9000L
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        assertTrue("outliving one sentinel only makes it a suspect", suspects().isEmpty())

        gcCycleCount = 3L
        assertNull("confirming ends the sentinel chain", detector.onSentinelReclaimed(confirmation))

        val suspect = suspects().single()
        assertEquals(5000L, suspect.trackedAtMs)
        assertSame(token, suspect.token)
        assertEquals("surviving the sentinel that confirmed it already counts as one cycle survived", 1L, suspect.cyclesSurvived)
        assertEquals(System.identityHashCode(leaked), suspect.id)

        gcCycleCount = 7L
        assertEquals(
            "4 more cycles have passed since confirmation, on top of the 1 already counted",
            5L,
            suspects().single().cyclesSurvived,
        )
    }

    @Test
    fun `a report counts the cycles each suspect survived since the previous report`() {
        // held strongly for the duration of the test so that neither can be collected
        val earlier = Any()
        val later = Any()
        gcCycleCount = 10L
        detector.trackOpened(earlier, OWNER)
        confirmAfterSecondSentinel(checkNotNull(detector.trackClosed(earlier, OWNER, "earlier")))

        val first = detector.suspects(0L, 12L).single()
        assertEquals("a first report includes the confirming collection", 3L, first.cyclesSurvived)

        gcCycleCount = 14L
        detector.trackOpened(later, OWNER)
        confirmAfterSecondSentinel(checkNotNull(detector.trackClosed(later, OWNER, "later")))

        assertEquals(
            "one confirmed before the previous report counts from it, one confirmed since from its confirmation",
            mapOf("earlier" to 4L, "later" to 3L),
            detector.suspects(12L, 16L).associate { it.token to it.cyclesSurvived },
        )
        assertTrue("nothing survived since the previous report", detector.suspects(16L, 16L).isEmpty())
    }

    @Test
    fun `id identifies the referent and stays stable across repeated snapshots`() {
        val first = Any()
        val second = Any()

        detector.trackOpened(first, OWNER)
        detector.trackOpened(second, OWNER)
        val firstRef = checkNotNull(detector.trackClosed(first, OWNER, "first"))
        val secondRef = checkNotNull(detector.trackClosed(second, OWNER, "second"))
        confirmAfterSecondSentinel(firstRef)
        confirmAfterSecondSentinel(secondRef)

        val snapshots = suspects().associateBy { it.token }
        assertEquals(System.identityHashCode(first), snapshots.getValue("first").id)
        assertEquals(System.identityHashCode(second), snapshots.getValue("second").id)

        // stable across repeated snapshots, not re-randomized per call
        assertEquals(snapshots.getValue("first").id, suspects().single { it.token == "first" }.id)
    }

    @Test
    fun `an object released before its second sentinel is reclaimed does not become a suspect`() {
        val brieflyHeld = Any()
        detector.trackOpened(brieflyHeld, OWNER)
        val ref = checkNotNull(detector.trackClosed(brieflyHeld, OWNER))

        // something was still holding it as the lifecycle ended, so it outlives the first sentinel
        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))

        // that hold is released, so the collection reclaiming the second sentinel takes it too
        confirmation.target.clear()

        assertNull(detector.onSentinelReclaimed(confirmation))
        assertTrue("a hold that was released is not a leak", suspects().isEmpty())
    }

    @Test
    fun `the second sentinel tracks the same object as the first`() {
        val leaked = Any()
        detector.trackOpened(leaked, OWNER)
        val ref = checkNotNull(detector.trackClosed(leaked, OWNER, "token"))

        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))

        assertSame("the weak reference is carried over rather than reallocated", ref.target, confirmation.target)
        assertEquals(ref.trackedAtMs, confirmation.trackedAtMs)
        assertSame(ref.token, confirmation.token)
    }

    @Test
    fun `an object collected alongside its sentinel never becomes a suspect`() {
        val collected = Any()
        detector.trackOpened(collected, OWNER)
        val ref = checkNotNull(detector.trackClosed(collected, OWNER))

        // the collection that reclaimed the sentinel reclaimed the tracked object too
        ref.target.clear()

        assertNull("a collected object is never suspected", detector.onSentinelReclaimed(ref))
        assertTrue("an object that was collected is not a leak", suspects().isEmpty())
    }

    @Test
    fun `an object with no sentinel is not tracked when its lifecycle ends`() {
        val neverOpened = Any()

        assertNull("without a sentinel there is nothing to compare against", detector.trackClosed(neverOpened, OWNER))
    }

    @Test
    fun `opening the same object twice does not create a second sentinel`() {
        val reopened = Any()

        detector.trackOpened(reopened, OWNER)
        detector.trackOpened(reopened, OWNER)

        assertNotNull(detector.trackClosed(reopened, OWNER))
        assertNull("the second open must not have added a sentinel of its own", detector.trackClosed(reopened, OWNER))
    }

    @Test
    fun `a close from an owner that did not open the object is ignored`() {
        val shared = Any()
        detector.trackOpened(shared, OWNER)

        assertNull("only the owner that opened it can close it", detector.trackClosed(shared, OTHER_OWNER, "other"))

        val ref = checkNotNull(
            detector.trackClosed(shared, OWNER, "owner"),
            { "an ignored close must leave the opener's sentinel in place" },
        )
        assertEquals("owner", ref.token)
    }

    @Test
    fun `opening an object already opened by another owner does not take it over`() {
        val shared = Any()
        detector.trackOpened(shared, OWNER)
        detector.trackOpened(shared, OTHER_OWNER)

        assertNull("the first owner to open it keeps it", detector.trackClosed(shared, OTHER_OWNER))
        assertNotNull(detector.trackClosed(shared, OWNER))
    }

    @Test
    fun `reopening an object after it was closed stops tracking it`() {
        val reused = Any()
        detector.trackOpened(reused, OWNER)
        val ref = checkNotNull(detector.trackClosed(reused, OWNER))

        detector.trackOpened(reused, OTHER_OWNER)

        assertNull("the previous lifecycle is no longer watched", detector.onSentinelReclaimed(ref))
        assertNull("not by the owner that first opened it", detector.trackClosed(reused, OWNER))
        assertNull("nor by the owner that reopened it", detector.trackClosed(reused, OTHER_OWNER))

        detector.trackOpened(reused, OWNER)
        assertNull("nor in any later lifecycle", detector.trackClosed(reused, OWNER))
    }

    @Test
    fun `reopening an object awaiting confirmation cancels the confirmation`() {
        val reused = Any()
        detector.trackOpened(reused, OWNER)
        val confirmation = checkNotNull(detector.onSentinelReclaimed(checkNotNull(detector.trackClosed(reused, OWNER))))

        detector.trackOpened(reused, OWNER)

        assertNull(detector.onSentinelReclaimed(confirmation))
        assertTrue("an object put back into use is not a leak", suspects().isEmpty())
    }

    @Test
    fun `reopening a confirmed suspect discards it`() {
        val reused = Any()
        detector.trackOpened(reused, OWNER)
        confirmAfterSecondSentinel(checkNotNull(detector.trackClosed(reused, OWNER)))
        assertEquals(1, suspects().size)

        detector.trackOpened(reused, OWNER)

        assertTrue("an object put back into use is not a leak", suspects().isEmpty())
        assertTrue(detector.confirmedSuspects().isEmpty())
    }

    @Test
    fun `each tracked object becomes a suspect independently of the others`() {
        val first = Any()
        val second = Any()

        detector.trackOpened(first, OWNER)
        detector.trackOpened(second, OWNER)
        val firstRef = checkNotNull(detector.trackClosed(first, OWNER, "first"))
        val secondRef = checkNotNull(detector.trackClosed(second, OWNER, "second"))

        confirmAfterSecondSentinel(firstRef)
        assertEquals(listOf("first"), suspects().map { it.token })

        confirmAfterSecondSentinel(secondRef)
        assertEquals(setOf("first", "second"), suspects().map { it.token }.toSet())
    }

    @Test
    fun `a confirmed suspect is only added once`() {
        val leaked = Any()
        detector.trackOpened(leaked, OWNER)
        val ref = checkNotNull(detector.trackClosed(leaked, OWNER))

        val confirmation = confirmAfterSecondSentinel(ref)
        assertEquals(1, suspects().size)

        repeat(5) {
            detector.onSentinelReclaimed(ref)
            detector.onSentinelReclaimed(confirmation)
        }

        assertEquals(1, suspects().size)
    }

    @Test
    fun `a suspect that is actually collected is discarded from the dataset`() {
        val leaked = Any()
        detector.trackOpened(leaked, OWNER)
        val ref = checkNotNull(detector.trackClosed(leaked, OWNER))
        confirmAfterSecondSentinel(ref)

        assertEquals(1, suspects().size)

        val suspect = detector.confirmedSuspects().single()
        detector.onSuspectCollected(suspect)

        assertTrue("an object that was actually collected is not a leak", suspects().isEmpty())
    }

    @Test
    fun `a suspect that has already cleared is omitted from a snapshot`() {
        val leaked = Any()
        detector.trackOpened(leaked, OWNER)
        val ref = checkNotNull(detector.trackClosed(leaked, OWNER))
        confirmAfterSecondSentinel(ref)

        // simulates the object actually being collected before suspects() reads it, without waiting for a real one or
        // for onSuspectCollected to catch up via the queue
        detector.confirmedSuspects().single().clear()

        assertTrue("a suspect with nothing left to read is not reported", suspects().isEmpty())
    }

    @Test
    fun `nothing is tracked as a suspect after the detector stops`() {
        val leaked = Any()
        detector.start()
        detector.trackOpened(leaked, OWNER)
        val ref = checkNotNull(detector.trackClosed(leaked, OWNER))

        detector.stop()
        detector.onSentinelReclaimed(ref)

        assertTrue(suspects().isEmpty())
    }

    @Test
    fun `an exception dispatching on the detector thread is tracked and later references are still dispatched`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val readFailed = AtomicBoolean(false)
        detector = LeakDetector(
            clock = { now },
            errorHandler = errorHandler,
            versionChecker = BuildVersionChecker,
            gcCycleCountReader = {
                // only the detector thread fails, so that polling suspects() from this thread can't take the failure
                if (Thread.currentThread().name == LeakDetector.THREAD_NAME && readFailed.compareAndSet(false, true)) {
                    error("failed to read GC count")
                }
                gcCycleCount.toString()
            },
        )
        detector.start()

        // held strongly for the duration of the test so that they cannot be collected
        val leaked = List(2) { Any() }
        leaked.forEach { referent ->
            detector.trackOpened(referent, OWNER)
            val ref = checkNotNull(detector.trackClosed(referent, OWNER))
            // the first reclaim is driven directly, and the confirming one through the detector thread's queue
            checkNotNull(detector.onSentinelReclaimed(ref)).enqueue()
        }

        val deadline = System.currentTimeMillis() + THREAD_EXIT_TIMEOUT_MS
        while (suspects().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertEquals("only the reference whose dispatch didn't throw is confirmed", 1, suspects().size)
        assertEquals(SdkErrorType.DataSourceDataCaptureFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertTrue(checkNotNull(detectorThread()).isAlive)
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
                    detector.trackOpened(referent, OWNER)
                    val ref = checkNotNull(detector.trackClosed(referent, OWNER, threadIndex * opsPerThread + op))
                    if (detector.trackClosed(referent, OWNER) != null) {
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
        assertEquals(total - collectedCount, suspects().size)
        assertEquals(
            (0 until total).filter { it % 4 != 0 }.toSet(),
            suspects().map { it.token }.toSet(),
        )
        assertTrue(
            "every reference has already been handled, so none may still be watched",
            reclaimed.all { detector.onSentinelReclaimed(it) == null },
        )
    }

    @Test
    fun `concurrent reopens and onSentinelReclaimed never report a reused object`() {
        val threadCount = 8
        val opsPerThread = 500
        val total = threadCount * opsPerThread
        // held strongly for the duration of the test so that nothing tracked can be collected for real
        val referents = ConcurrentLinkedQueue<Any>()
        val closedRefs = LinkedBlockingQueue<LeakDetector.TrackedReference>()

        val workers = List(threadCount) { _ ->
            {
                repeat(opsPerThread) {
                    val referent = Any()
                    referents.add(referent)
                    detector.trackOpened(referent, OWNER)
                    closedRefs.add(checkNotNull(detector.trackClosed(referent, OWNER)))
                    detector.trackOpened(referent, OWNER)
                    if (detector.trackClosed(referent, OWNER) != null) {
                        error("a reused object must not be tracked again")
                    }
                }
            }
        }
        // each lifecycle can be reclaimed, and even confirmed, before, during or after its object is reopened
        val reclaimer: () -> Unit = {
            repeat(total) {
                val ref = checkNotNull(closedRefs.poll(10, TimeUnit.SECONDS))
                detector.onSentinelReclaimed(ref)?.let { detector.onSentinelReclaimed(it) }
            }
        }

        runActionsConcurrently(workers + reclaimer)

        assertEquals(total, referents.size)
        assertTrue("no reused object may be reported", suspects().isEmpty())
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

    /**
     * Every suspect, with the cycles it has survived since the counter started.
     */
    private fun suspects(): List<LeakDetector.LeakSnapshot> = detector.suspects(0L, gcCycleCount)

    private fun detectorThread(): Thread? =
        Thread.getAllStackTraces().keys.firstOrNull { it.name == LeakDetector.THREAD_NAME }

    private companion object {
        const val THREAD_EXIT_TIMEOUT_MS = 5000L

        // any two distinct owners - which instrumentations they belong to doesn't matter here
        val OWNER = TrackingSource.ACTIVITY
        val OTHER_OWNER = TrackingSource.FRAGMENT
    }
}
