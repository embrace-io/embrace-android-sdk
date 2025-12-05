package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.BLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.UNBLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageSample
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BASELINE_MS = 16000000000

internal class AnrStacktraceSamplerTest {

    private val thread = Thread.currentThread()
    private val clock = FakeClock()
    private val configService = FakeConfigService()
    private val state = ThreadMonitoringState(clock)
    private val worker = BackgroundWorker(
        BlockingScheduledExecutorService()
    )

    @Test
    fun testLeastValuableInterval() {
        val sampler = AnrStacktraceSampler(
            clock,
            thread,
            worker,
            configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            configService.anrBehavior.getMaxStacktracesPerInterval(),
            configService.anrBehavior.getStacktraceFrameLimit(),
        )
        assertNull(sampler.findLeastValuableIntervalWithSamples())
        val interval1 = ThreadBlockageInterval(
            startTime = BASELINE_MS,
            lastKnownTime = BASELINE_MS + 5000,
            samples = emptyList()
        )
        val interval2 = ThreadBlockageInterval(
            startTime = BASELINE_MS,
            lastKnownTime = BASELINE_MS + 4000,
            samples = emptyList()
        )
        val interval3 = ThreadBlockageInterval(
            startTime = BASELINE_MS,
            lastKnownTime = BASELINE_MS + 1000,
            samples = emptyList()
        )
        val interval4 = ThreadBlockageInterval(
            startTime = BASELINE_MS,
            lastKnownTime = BASELINE_MS + 1000,
            samples = emptyList()
        )
        val interval5 = ThreadBlockageInterval(
            startTime = BASELINE_MS,
            lastKnownTime = BASELINE_MS + 500,
            code = ThreadBlockageInterval.CODE_SAMPLES_CLEARED
        )

        sampler.threadBlockageIntervals.add(interval1)
        assertEquals(interval1, sampler.findLeastValuableIntervalWithSamples())

        sampler.threadBlockageIntervals.add(interval2)
        assertEquals(interval2, sampler.findLeastValuableIntervalWithSamples())

        sampler.threadBlockageIntervals.add(interval3)
        assertEquals(interval3, sampler.findLeastValuableIntervalWithSamples())

        // most recent interval gets binned if duration is equal
        sampler.threadBlockageIntervals.add(interval4)
        assertEquals(interval3, sampler.findLeastValuableIntervalWithSamples())

        // intervals without any samples are ignored
        sampler.threadBlockageIntervals.add(interval5)
        assertEquals(interval3, sampler.findLeastValuableIntervalWithSamples())
    }

    @Test
    fun `exceed sample limit for one ANR interval`() {
        clock.setCurrentTime(BASELINE_MS)
        val repeatCount = 100
        val intervalMs: Long = 100
        val sampler = AnrStacktraceSampler(
            clock,
            thread,
            worker,
            configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            configService.anrBehavior.getMaxStacktracesPerInterval(),
            configService.anrBehavior.getStacktraceFrameLimit(),
        )

        // simulate one ANR with 100 intervals
        sampler.onThreadBlockageEvent(BLOCKED, clock.now())

        repeat(repeatCount) {
            sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            clock.tick(intervalMs)
        }

        sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())

        // verify one interval recorded
        val intervals = sampler.getAnrIntervals(state, clock)
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
    fun `exceed limit for number of ANRs`() {
        clock.setCurrentTime(BASELINE_MS)
        val anrRepeatCount = 15
        val intervalRepeatCount = 100
        val intervalMs: Long = 100
        val sampler = AnrStacktraceSampler(
            clock,
            thread,
            worker,
            configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            configService.anrBehavior.getMaxStacktracesPerInterval(),
            configService.anrBehavior.getStacktraceFrameLimit(),
        )

        // simulate multiple ANRs with intervals
        repeat(anrRepeatCount) { index ->
            sampler.onThreadBlockageEvent(BLOCKED, clock.now())

            repeat(intervalRepeatCount + index) {
                sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
                clock.tick(intervalMs)
            }
            sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        }

        // verify 15 intervals were recorded
        val intervals = sampler.getAnrIntervals(state, clock)
        assertEquals(anrRepeatCount, intervals.size)

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
    fun `verify hard limit of 100 anr intervals`() {
        clock.setCurrentTime(BASELINE_MS)
        val anrRepeatCount = 110
        val intervalMs: Long = 100
        val sampler = AnrStacktraceSampler(
            clock,
            thread,
            worker,
            configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            configService.anrBehavior.getMaxStacktracesPerInterval(),
            configService.anrBehavior.getStacktraceFrameLimit(),
        )

        // simulate 110 ANRs with intervals
        repeat(anrRepeatCount) {
            sampler.onThreadBlockageEvent(BLOCKED, clock.now())
            sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            clock.tick(intervalMs)
            sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        }

        // verify maximum of 100 intervals were recorded
        val intervals = sampler.getAnrIntervals(state, clock)
        assertEquals(100, intervals.size)
    }

    @Test
    fun `verify truncation of ANR stacktrace respects the config`() {
        configService.anrBehavior = FakeAnrBehavior(frameLimit = 5)

        val sampler = AnrStacktraceSampler(
            clock,
            thread,
            worker,
            configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            configService.anrBehavior.getMaxStacktracesPerInterval(),
            configService.anrBehavior.getStacktraceFrameLimit(),
        )

        sampler.onThreadBlockageEvent(BLOCKED, clock.now())
        sampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(5000)
        sampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        val intervals = sampler.getAnrIntervals(state, clock)
        val interval = intervals.single()
        interval.samples?.forEach { sample ->
            sample.threads?.forEach { thread ->
                val lines = checkNotNull(thread.lines)
                assertEquals(5, lines.size)
                assertTrue(thread.frameCount > lines.size)
            }
        }
    }
}
