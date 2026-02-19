package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeThreadBlockageBehavior
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.UNBLOCKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val BASELINE_MS = 16000000000

internal class ThreadBlockageSamplerTest {
    private val thread = Thread.currentThread()
    private val clock = FakeClock()
    private val behavior = FakeConfigService().threadBlockageBehavior
    private lateinit var sampler: ThreadBlockageSampler

    @Before
    fun setup() {
        clock.setCurrentTime(BASELINE_MS)
        sampler = ThreadBlockageSampler(
            clock,
            thread,
            behavior.getMaxIntervalsPerSession(),
            behavior.getMaxStacktracesPerInterval(),
            behavior.getStacktraceFrameLimit(),
        )
    }

    @Test
    fun testLeastValuableInterval() {
        sampler.apply {
            createThreadBlockageInterval(clock, 10000)
            createThreadBlockageInterval(clock, 8000)
            createThreadBlockageInterval(clock, 6000)
            createThreadBlockageInterval(clock, 4000)
            createThreadBlockageInterval(clock, 2000)
            createThreadBlockageInterval(clock, 1500)
            createThreadBlockageInterval(clock, 1200)
        }

        val intervals = sampler.getThreadBlockageIntervals()
        assertEquals(
            listOf(10000L, 8000L, 6000L, 4000L, 2000L),
            intervals.filter { it.samples != null }.map { checkNotNull(it.endTime) - it.startTime }
        )
        assertEquals(
            listOf(1500L, 1200L),
            intervals.filter {
                it.samples == null
            }.map { checkNotNull(it.endTime) - it.startTime }
        )
    }

    @Test
    fun `exceed sample limit for one thread blockage interval`() {
        val repeatCount = 100
        val intervalMs: Long = 100

        // simulate one thread blockage with 100 intervals
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())

        repeat(repeatCount) {
            sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            clock.tick(intervalMs)
        }

        sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())

        // verify one interval recorded
        val intervals = sampler.getThreadBlockageIntervals()
        assertEquals(1, intervals.size)

        // verify basic metadata about the interval
        val interval = intervals.single()
        assertEquals(BASELINE_MS, interval.startTime)
        assertEquals(clock.now(), interval.endTime)
        assertEquals(ThreadBlockageInterval.CODE_DEFAULT, interval.code)

        // verify samples were captured up to the limit
        val samples = checkNotNull(interval.samples)
        assertEquals(repeatCount, samples.size)

        // verify timestamps match
        samples.forEachIndexed { index, sample ->
            val expected = BASELINE_MS + (index * intervalMs)
            assertEquals(expected, sample.timestamp)
        }

        // verify samples after the sample limit record a code that they are cleared
        samples.forEachIndexed { index, sample ->
            val expected = when {
                index >= 80 -> ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED
                else -> ThreadBlockageSample.CODE_DEFAULT
            }
            assertEquals(expected, sample.code)
        }
    }

    @Test
    fun `exceed limit for number of thread blockages`() {
        val threadBlockageRepeatCount = 15
        val intervalRepeatCount = 100
        val intervalMs: Long = 100

        // simulate multiple thread blockages
        repeat(threadBlockageRepeatCount) { index ->
            sampler.onThreadBlockageEvent(BLOCKED, clock.now())

            repeat(intervalRepeatCount + index) {
                sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
                clock.tick(intervalMs)
            }
            sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        }

        // verify 15 intervals were recorded
        val intervals = sampler.getThreadBlockageIntervals()
        assertEquals(threadBlockageRepeatCount, intervals.size)

        // verify basic metadata about each interval
        intervals.forEachIndexed { index, interval ->
            if (index >= 10) {
                assertEquals(ThreadBlockageInterval.CODE_DEFAULT, interval.code)
                assertNotNull(interval.samples)
            } else {
                assertEquals(ThreadBlockageInterval.CODE_SAMPLES_CLEARED, interval.code)
                assertNull(interval.samples)
            }
        }

        // verify samples were cleared in order of priority (longest intervals are retained)
        intervals.forEachIndexed { index, interval ->
            if (index < 10) {
                return
            }
            assertEquals(intervalRepeatCount + index, interval.samples?.size)
        }
    }

    @Test
    fun `verify hard limit of 100 intervals`() {
        val intervalRepeatCount = 110
        val intervalMs: Long = 100

        // simulate 110 intervals
        repeat(intervalRepeatCount) {
            sampler.onThreadBlockageEvent(BLOCKED, clock.now())
            sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            clock.tick(intervalMs)
            sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        }

        // verify maximum of 100 intervals were recorded
        val intervals = sampler.getThreadBlockageIntervals()
        assertEquals(100, intervals.size)
    }

    @Test
    fun `verify truncation of stacktrace respects the config`() {
        val behavior = FakeThreadBlockageBehavior(frameLimit = 5)
        val sampler = ThreadBlockageSampler(
            clock,
            thread,
            behavior.getMaxIntervalsPerSession(),
            behavior.getMaxStacktracesPerInterval(),
            behavior.getStacktraceFrameLimit(),
        )

        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(5000)
        sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        val intervals = sampler.getThreadBlockageIntervals()
        val interval = intervals.single()
        interval.samples?.forEach { sample ->
            sample.threadSample?.let { thread ->
                val lines = checkNotNull(thread.lines)
                assertEquals(5, lines.size)
                assertTrue(thread.frameCount > lines.size)
            }
        }
    }

    @Test
    fun `reader after BLOCKED sees valid in-progress interval`() {
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        val intervals = sampler.getThreadBlockageIntervals()
        assertEquals(1, intervals.size)
        val interval = intervals.single()
        assertNull(interval.endTime)
        assertNotNull(interval.lastKnownTime)
        assertNotNull(interval.samples)
        assertEquals(0, interval.samples?.size)
    }

    @Test
    fun `full lifecycle with reader at each phase boundary`() {
        // Phase 1: Before any events — no intervals
        assertEquals(0, sampler.getThreadBlockageIntervals().size)

        // Phase 2: BLOCKED
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        val afterBlocked = sampler.getThreadBlockageIntervals()
        assertEquals(1, afterBlocked.size)
        assertNull(afterBlocked[0].endTime)
        assertEquals(BASELINE_MS, afterBlocked[0].startTime)
        assertNotNull(afterBlocked[0].samples)

        // Phase 3: BLOCKED_INTERVAL (capture a sample)
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        val afterInterval = sampler.getThreadBlockageIntervals()
        assertEquals(1, afterInterval.size)
        assertNull(afterInterval[0].endTime)
        assertEquals(1, afterInterval[0].samples?.size)

        // Phase 4: More samples
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        val afterMoreIntervals = sampler.getThreadBlockageIntervals()
        assertEquals(2, afterMoreIntervals[0].samples?.size)

        // Phase 5: UNBLOCKED
        clock.tick(1000)
        sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        val afterUnblocked = sampler.getThreadBlockageIntervals()
        assertEquals(1, afterUnblocked.size)
        assertNotNull(afterUnblocked[0].endTime)
        assertEquals(BASELINE_MS, afterUnblocked[0].startTime)
        assertEquals(BASELINE_MS + 1200, afterUnblocked[0].endTime)
        assertEquals(2, afterUnblocked[0].samples?.size)

        // Phase 6: After UNBLOCKED — only the completed interval remains
        assertEquals(1, sampler.getThreadBlockageIntervals().size)
        assertNotNull(sampler.getThreadBlockageIntervals()[0].endTime)
    }

    @Test
    fun `concurrent UNBLOCKED and read produces correct result`() {
        val unblockedTime = BASELINE_MS + 1100
        sampler.onThreadBlockageEvent(BLOCKED, BASELINE_MS)
        clock.setCurrentTime(BASELINE_MS + 100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, BASELINE_MS + 100)
        clock.setCurrentTime(unblockedTime)

        val barrier = CountDownLatch(1)
        val writerDone = CountDownLatch(1)

        Thread {
            barrier.await()
            sampler.onThreadBlockageEvent(UNBLOCKED, unblockedTime)
            writerDone.countDown()
        }.start()

        barrier.countDown()
        val intervals = sampler.getThreadBlockageIntervals()
        assertTrue(writerDone.await(1, TimeUnit.SECONDS))

        // Safety contract: exactly 1 interval, correct start time, no crash
        assertEquals(1, intervals.size)
        assertEquals(BASELINE_MS, intervals[0].startTime)

        // Deterministic final state after writer completes
        val finalIntervals = sampler.getThreadBlockageIntervals()
        assertEquals(1, finalIntervals.size)
        assertNotNull(finalIntervals[0].endTime)
        assertEquals(BASELINE_MS, finalIntervals[0].startTime)
        assertEquals(unblockedTime, finalIntervals[0].endTime)
    }

    @Test
    fun `concurrent UNBLOCKED plus new BLOCKED and read produces correct result`() {
        val b1UnblockedTime = BASELINE_MS + 1100
        val b2BlockedTime = BASELINE_MS + 1150
        sampler.onThreadBlockageEvent(BLOCKED, BASELINE_MS)
        clock.setCurrentTime(BASELINE_MS + 100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, BASELINE_MS + 100)
        clock.setCurrentTime(b1UnblockedTime)

        val barrier = CountDownLatch(1)
        val writerDone = CountDownLatch(1)

        Thread {
            barrier.await()
            sampler.onThreadBlockageEvent(UNBLOCKED, b1UnblockedTime)
            sampler.onThreadBlockageEvent(BLOCKED, b2BlockedTime)
            writerDone.countDown()
        }.start()

        barrier.countDown()
        val intervals = sampler.getThreadBlockageIntervals()
        assertTrue(writerDone.await(1, TimeUnit.SECONDS))

        // Safety contract: at least 1 interval, B1's start time present, no crash
        assertTrue(intervals.isNotEmpty())
        assertTrue(intervals.any { it.startTime == BASELINE_MS })

        // Deterministic final state: B1 completed + B2 in-progress
        clock.setCurrentTime(b2BlockedTime)
        val finalIntervals = sampler.getThreadBlockageIntervals()
        assertEquals(2, finalIntervals.size)
        // B1: completed
        assertNotNull(finalIntervals[0].endTime)
        assertEquals(BASELINE_MS, finalIntervals[0].startTime)
        assertEquals(b1UnblockedTime, finalIntervals[0].endTime)
        // B2: in-progress
        assertNull(finalIntervals[1].endTime)
        assertEquals(b2BlockedTime, finalIntervals[1].startTime)
    }

    @Test
    fun `reader returns in-progress interval when validation passes`() {
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(500)

        // No hook — validation will pass (currentBlockage unchanged)
        val intervals = sampler.getThreadBlockageIntervals()

        assertEquals(1, intervals.size)
        val interval = intervals.single()
        assertNull(interval.endTime)
        assertNotNull(interval.lastKnownTime)
        assertEquals(BASELINE_MS, interval.startTime)
        assertEquals(1, interval.samples?.size)
    }

    @Test
    fun `writer on separate thread publishes completed interval to reader`() {
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(1000)

        val writerDone = CountDownLatch(1)

        Thread {
            sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
            writerDone.countDown()
        }.start()
        assertTrue(writerDone.await(1, TimeUnit.SECONDS))

        val intervals = sampler.getThreadBlockageIntervals()
        assertEquals(1, intervals.size)
        assertNotNull(intervals[0].endTime)
        assertEquals(BASELINE_MS, intervals[0].startTime)
    }

    @Test
    fun `sample snapshot is not mutated by subsequent captureSample calls`() {
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())

        // Snapshot 1: one sample
        val snapshot1 = sampler.getThreadBlockageIntervals()
        assertEquals(1, snapshot1[0].samples?.size)

        // Capture two more samples
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(100)
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())

        // Snapshot 1 is unchanged (snapshot isolation)
        assertEquals(1, checkNotNull(snapshot1[0].samples).size)

        // New read sees all three samples
        val snapshot2 = sampler.getThreadBlockageIntervals()
        assertEquals(3, checkNotNull(snapshot2[0].samples).size)
    }

    @Test
    fun `concurrent captureSample and getThreadBlockageIntervals does not throw`() {
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())

        val startBarrier = CountDownLatch(1)
        val writerDone = CountDownLatch(1)
        val readerDone = CountDownLatch(1)
        val readerException = AtomicReference<Throwable?>()

        // Writer: add 50 samples
        Thread {
            startBarrier.await()
            repeat(50) {
                clock.tick(100)
                sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            }
            writerDone.countDown()
        }.start()

        // Reader: read intervals 50 times
        Thread {
            startBarrier.await()
            try {
                repeat(50) {
                    sampler.getThreadBlockageIntervals()
                }
            } catch (e: Throwable) {
                readerException.set(e)
            }
            readerDone.countDown()
        }.start()

        // Release both threads simultaneously
        startBarrier.countDown()
        assertTrue(writerDone.await(1, TimeUnit.SECONDS))
        assertTrue(readerDone.await(1, TimeUnit.SECONDS))

        val exception = readerException.get()
        assertNull(
            "Concurrent access threw: ${exception?.javaClass?.simpleName}: ${exception?.message}",
            exception
        )
    }

    private fun ThreadBlockageSampler.createThreadBlockageInterval(clock: FakeClock, duration: Long) {
        onThreadBlockageEvent(BLOCKED, clock.now())
        onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(duration)
        onThreadBlockageEvent(UNBLOCKED, clock.now())
    }
}
