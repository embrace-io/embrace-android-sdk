package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.BLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.UNBLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageSample
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.currentThread
import java.util.concurrent.TimeUnit

internal class AnrServiceImplTest {

    private lateinit var watchdogExecutorService: SingleThreadTestScheduledExecutor

    @Rule
    @JvmField
    val rule = AnrServiceRule(
        scheduledExecutorSupplier = {
            watchdogExecutorService = SingleThreadTestScheduledExecutor()
            watchdogExecutorService
        }
    )

    @Before
    fun setUp() {
        watchdogExecutorService.reset()
        watchdogExecutorService.submit { rule.watchdogMonitorThread.set(currentThread()) }
            .get(1L, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        watchdogExecutorService.shutdown()
        watchdogExecutorService.awaitTermination(1L, TimeUnit.SECONDS)
        assertFalse(watchdogExecutorService.executing.get())
    }

    @Test
    fun testFinishInitialization() {
        with(rule) {
            val configService = FakeConfigService()
            watchdogExecutorService.submit {
                anrService.startCapture()
            }.get(1L, TimeUnit.SECONDS)
            // verify the config service was changed from the bootstrapped early version
            assertNotSame(this.fakeConfigService, configService)
        }
    }

    @Test
    fun testColdStartIgnored() {
        with(rule) {
            // cold starts should always be ignored
            state.lastTargetThreadResponseMs = 1
            anrService.onForeground()

            // assert no ANR interval was added
            assertEquals(0, stacktraceSampler.getAnrIntervals().size)
        }
    }

    @Test
    fun testCleanCollections() {
        with(rule) {
            // assert the ANR interval was added
            createThreadBlockageInterval()

            // create in progress interval
            stacktraceSampler.onThreadBlockageEvent(BLOCKED, clock.now())
            stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            state.threadBlockageInProgress = true

            val intervals = stacktraceSampler.getAnrIntervals()
            assertEquals(2, intervals.size)

            // the ANR interval should be removed here
            anrService.cleanCollections()
            watchdogExecutorService.shutdown()
            watchdogExecutorService.awaitTermination(1, TimeUnit.SECONDS)
            assertEquals(1, stacktraceSampler.getAnrIntervals().size)
        }
    }

    @Test
    fun testGetIntervals() {
        with(rule) {
            repeat(5) {
                createThreadBlockageInterval()
            }

            val intervals = anrService.snapshotSpans()
            assertEquals(5, intervals.size)
        }
    }

    @Test
    fun testGetIntervalsAnrInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            state.threadBlockageInProgress = true

            // assert only one interval was added from the anrInProgress flag
            val intervals = anrService.snapshotSpans()
            val interval = intervals.single()
            assertEquals(0L, interval.startTimeNanos?.nanosToMillis())
            assertEquals(500L, interval.endTimeNanos?.nanosToMillis())
            assertEquals(500L, interval.lastKnownTime?.nanosToMillis())
        }
    }

    @Test
    fun testGetIntervalsCrashInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            state.threadBlockageInProgress = true

            // assert only one interval was added from the anrInProgress flag
            val intervals = anrService.snapshotSpans()
            assertEquals(1, intervals.size)
        }
    }

    @Test
    fun testGetIntervalsWithStacktraces() {
        with(rule) {
            // create an ANR service with one stacktrace
            clock.setCurrentTime(15020000L)

            state.threadBlockageInProgress = true
            state.lastTargetThreadResponseMs = 15000000L
            stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            assertEquals(1, stacktraceSampler.getAnrIntervals().size)

            // assert only one interval was added from the anrInProgress flag
            val intervals = anrService.snapshotSpans()
            val interval = intervals.single()
            assertEquals(15000000L, interval.startTimeNanos?.nanosToMillis())
            assertEquals(15020000L, interval.endTimeNanos?.nanosToMillis())
            assertEquals(15020000L, interval.lastKnownTime?.nanosToMillis())

            val tick = checkNotNull(interval.events?.single())
            assertEquals(15020000L, tick.timestampNanos?.nanosToMillis())
            assertNotNull(tick.attributes?.find { it.key == ExceptionAttributes.EXCEPTION_STACKTRACE })
        }
    }

    @Test
    fun testAnrIntervalStartAndEndTimes() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                val anrStartTs = 15020000L
                val anrInProgressTs = 15021500L
                val anrEndTs = 15023000L
                clock.setCurrentTime(anrStartTs)

                blockedThreadDetector.onMonitorThreadInterval(anrStartTs)
                state.lastTargetThreadResponseMs = anrStartTs
                blockedThreadDetector.onTargetThreadProcessedMessage(anrStartTs)
                assertFalse(state.threadBlockageInProgress)

                blockedThreadDetector.onMonitorThreadInterval(
                    anrInProgressTs
                )
                assertTrue(state.threadBlockageInProgress)

                state.lastTargetThreadResponseMs = anrEndTs
                blockedThreadDetector.onTargetThreadProcessedMessage(anrEndTs)
                assertFalse(state.threadBlockageInProgress)

                val intervals = anrService.snapshotSpans()
                assertEquals(1, intervals.size)
                val interval = intervals[0]
                assertEquals(anrStartTs, interval.startTimeNanos?.nanosToMillis())
                assertEquals(anrEndTs, interval.endTimeNanos?.nanosToMillis())
                assertNull(interval.lastKnownTime)
            }
        }
    }

    @Test
    fun `test ANR state is reset when onForeground is executed to prevent false positive ANR`() {
        val anrStartTs = 15020000L
        val anrEndTs = 15023000L
        with(rule) {
            clock.setCurrentTime(anrStartTs)
            anrService.onForeground()
            anrService.onBackground()
            clock.setCurrentTime(anrEndTs)
            anrService.onForeground()
            // Since Looper is a mock, we execute this operation to
            // ensure onMainThreadUnblocked runs and lastTargetThreadResponseMs gets updated
            simulateAnrRecovery()
            val intervals = anrService.snapshotSpans()
            assertEquals(0, intervals.size)
        }
    }

    private fun AnrServiceRule<SingleThreadTestScheduledExecutor>.simulateAnrRecovery() {
        blockedThreadDetector.onTargetThreadProcessedMessage(clock.now())
    }

    @Test
    fun `test timestamps are updated if onMainThreadUnblocked runs before onMonitorThreadHeartbeat to prevent false positive ANR`() {
        val anrStartTs = 15020000L
        val anrEndTs = 15023000L

        with(rule) {
            clock.setCurrentTime(anrStartTs)
            anrService.onForeground()
            anrService.onBackground()
            clock.setCurrentTime(anrEndTs)
            anrService.onForeground()
            simulateAnrRecovery()
            val intervals = anrService.snapshotSpans()
            assertEquals(0, intervals.size)
        }
    }

    @Test
    fun testAnrCaptureLimit() {
        executeSynchronouslyOnCurrentThread {
            // create an ANR service with one stacktrace
            with(rule) {
                clock.setCurrentTime(15020000L)
                val defaultLimit = 80
                val extra = 10
                val count = defaultLimit + extra

                repeat(count) {
                    stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
                }
                stacktraceSampler.onThreadBlockageEvent(UNBLOCKED, clock.now())

                assertEquals(1, stacktraceSampler.getAnrIntervals().size)

                val interval = checkNotNull(stacktraceSampler.getAnrIntervals().first())
                val samples = checkNotNull(interval.samples)
                assertEquals(count, samples.size)

                // after the default limit, samples are dropped samples are still serialized
                assertEquals(
                    extra,
                    samples.count { sample ->
                        sample.threadSample == null && sample.code == ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED
                    }
                )
                assertEquals(
                    defaultLimit,
                    samples.count { sample ->
                        sample.threadSample != null && sample.code == ThreadBlockageSample.CODE_DEFAULT
                    }
                )
            }
        }
    }

    @Test
    fun testReachedAnrCaptureLimit() {
        with(rule) {
            val limit = rule.anrBehavior.anrPerSessionImpl
            repeat(limit) { count ->
                createThreadBlockageInterval()
                assertEquals(count + 1, stacktraceSampler.getAnrIntervals().size)
            }
            createThreadBlockageInterval()
            assertEquals(5, stacktraceSampler.getAnrIntervals().filter { it.samples != null }.size)
            assertEquals(1, stacktraceSampler.getAnrIntervals().filter { it.samples == null }.size)
        }
    }

    @Test
    fun testBelowAnrDurationThreshold() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                // if the lastTimeThreadUnblocked is zero this should never be true
                state.lastTargetThreadResponseMs = 0
                state.lastMonitorThreadResponseMs = 0

                blockedThreadDetector.onMonitorThreadInterval(700)
                blockedThreadDetector.onMonitorThreadInterval(70000)
                assertTrue(stacktraceSampler.getAnrIntervals().isEmpty())
            }
        }
    }

    @Test
    fun testAboveAnrDurationThreshold() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                // if the lastTimeThreadUnblocked is above the threshold return true
                val now = 100L
                recreateService()
                state.lastTargetThreadResponseMs = now
                state.lastMonitorThreadResponseMs = now

                blockedThreadDetector.onMonitorThreadInterval(now + 500)
                blockedThreadDetector.onMonitorThreadInterval(now + 1000)
                blockedThreadDetector.onMonitorThreadInterval(now + 1001)
                blockedThreadDetector.onMonitorThreadInterval(now + 10000)

                val samples = checkNotNull(stacktraceSampler.getAnrIntervals().single().samples)
                assertEquals(2, samples.size)
                assertEquals(now + 1001, samples[0].timestamp)
                assertEquals(now + 10000, samples[1].timestamp)
            }
        }
    }

    @Test
    fun testMonitorThreadTimeout() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                // if the last response times greatly exceed the capture threshold it indicates the
                // process has been cached. We need to avoid a false positive in this case.

                val startTime = 150000000L
                val endTime = 150150239L
                clock.setCurrentTime(startTime)

                state.lastTargetThreadResponseMs = 0
                state.lastMonitorThreadResponseMs = startTime
                clock.setCurrentTime(endTime)

                // timestamp not updated if ANR threshold is not met
                blockedThreadDetector.onMonitorThreadInterval(startTime + 500)

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(clock.now(), state.lastMonitorThreadResponseMs)

                // timestamp not updated if ANR threshold is met
                state.lastTargetThreadResponseMs = 0
                blockedThreadDetector.onMonitorThreadInterval(startTime + 5000)

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(clock.now(), state.lastMonitorThreadResponseMs)

                // timestamp only updated if cached process threshold is reached
                blockedThreadDetector.onMonitorThreadInterval(startTime + 60001)

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(endTime, state.lastMonitorThreadResponseMs)
            }
        }
    }

    @Test
    fun `check ANR recovery`() {
        with(rule) {
            clock.setCurrentTime(100000L)
            simulateAnrRecovery()
            clock.tick(2000L)
            watchdogExecutorService.submit { blockedThreadDetector.onMonitorThreadInterval(clock.now()) }
                .get(1L, TimeUnit.SECONDS)
            simulateAnrRecovery()
            assertFalse(state.threadBlockageInProgress)
        }
    }

    @Test
    fun `test handleCrash stops ANR tracking but samples can still be retrieved`() {
        with(rule) {
            clock.setCurrentTime(14000000L)
            rule.anrBehavior.bgAnrCaptureEnabled = true
            anrService.onForeground()
            repeat(5) {
                createThreadBlockageInterval()
            }
            anrService.handleCrash("")
            val anrIntervals = anrService.snapshotSpans()
            assertEquals(5, anrIntervals.size)
        }
    }

    private fun AnrServiceRule<*>.createThreadBlockageInterval(duration: Long = 1000) {
        stacktraceSampler.onThreadBlockageEvent(BLOCKED, clock.now())
        stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
        clock.tick(duration)
        stacktraceSampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
    }

    /**
     * Some tests require running functions meant to be run in the ANR monitoring thread synchronously on the current thread to simulate
     * conditions that are to be tested. This allows the [enforceThread] to not fail by temporarily switching out the thread to be
     * compared against.
     */
    private fun executeSynchronouslyOnCurrentThread(action: () -> Unit) {
        with(rule) {
            synchronized(watchdogMonitorThread) {
                val previousAnrMonitoringThread = watchdogMonitorThread.get()
                watchdogMonitorThread.set(currentThread())
                action()
                watchdogMonitorThread.set(previousAnrMonitoringThread)
            }
        }
    }

    private val Span.lastKnownTime: Long?
        get() = attributes?.find { it.key == "last_known_time_unix_nano" }?.data?.toLong()
}
