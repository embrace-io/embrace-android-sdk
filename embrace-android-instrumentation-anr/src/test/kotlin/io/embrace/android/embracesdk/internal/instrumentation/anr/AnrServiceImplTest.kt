package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent.UNBLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageSample
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
            assertEquals(0, stacktraceSampler.threadBlockageIntervals.size)
        }
    }

    @Test
    fun testCleanCollections() {
        with(rule) {
            // assert the ANR interval was added
            val intervals = stacktraceSampler.threadBlockageIntervals
            intervals.add(ThreadBlockageInterval(startTime = 15000000, endTime = 15000100))
            val inProgressInterval = ThreadBlockageInterval(startTime = 15000000, lastKnownTime = 15000100)
            intervals.add(inProgressInterval)
            assertEquals(2, intervals.size)

            // the ANR interval should be removed here
            anrService.cleanCollections()
            watchdogExecutorService.shutdown()
            watchdogExecutorService.awaitTermination(1, TimeUnit.SECONDS)
            assertEquals(1, intervals.size)
            assertEquals(inProgressInterval, intervals.single())
        }
    }

    @Test
    fun testGetIntervals() {
        with(rule) {
            populateIntervals()

            val intervals = anrService.getCapturedData()
            assertEquals(5, intervals.size)
            assertEquals(14000000L, intervals[0].startTime)
            assertEquals(15000000L, intervals[1].startTime)
            assertEquals(15000500L, intervals[2].startTime)
            assertEquals(15001000L, intervals[3].startTime)
            assertEquals(16000000L, intervals[4].startTime)
        }
    }

    @Test
    fun testGetIntervalsAnrInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            state.threadBlockageInProgress = true

            // assert only one interval was added from the anrInProgress flag
            val intervals = anrService.getCapturedData()
            val interval = intervals.single()
            assertEquals(0, interval.startTime)
            assertNull(interval.endTime)
            assertEquals(500L, interval.lastKnownTime)
            assertNotNull(interval.samples)
        }
    }

    @Test
    fun testGetIntervalsCrashInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            state.threadBlockageInProgress = true

            // assert only one interval was added from the anrInProgress flag
            val intervals = anrService.getCapturedData()
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
            assertEquals(1, stacktraceSampler.size())

            // assert only one interval was added from the anrInProgress flag
            val intervals = anrService.getCapturedData()
            val interval = intervals.single()
            assertEquals(15000000L, interval.startTime)
            assertNull(interval.endTime)
            assertEquals(15020000L, interval.lastKnownTime)

            val tick = checkNotNull(interval.samples?.single())
            assertEquals(15020000, tick.timestamp)
            assertNotNull(tick.threads?.single())
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

                blockedThreadDetector.updateThreadBlockageTracking(anrStartTs)
                state.lastTargetThreadResponseMs = anrStartTs
                blockedThreadDetector.onTargetThreadResponse(anrStartTs)
                assertFalse(state.threadBlockageInProgress)

                blockedThreadDetector.updateThreadBlockageTracking(
                    anrInProgressTs
                )
                assertTrue(state.threadBlockageInProgress)

                state.lastTargetThreadResponseMs = anrEndTs
                blockedThreadDetector.onTargetThreadResponse(anrEndTs)
                assertFalse(state.threadBlockageInProgress)

                val intervals = anrService.getCapturedData()
                assertEquals(1, intervals.size)
                val interval = intervals[0]
                assertEquals(anrStartTs, interval.startTime)
                assertEquals(anrEndTs, interval.endTime)
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
            val intervals = anrService.getCapturedData()
            assertEquals(0, intervals.size)
        }
    }

    private fun AnrServiceRule<SingleThreadTestScheduledExecutor>.simulateAnrRecovery() {
        blockedThreadDetector.onTargetThreadResponse(clock.now())
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
            val intervals = anrService.getCapturedData()
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

                assertEquals(1, stacktraceSampler.threadBlockageIntervals.size)

                val interval = checkNotNull(stacktraceSampler.threadBlockageIntervals.first())
                val samples = checkNotNull(interval.samples)
                assertEquals(count, samples.size)

                // after the default limit, samples are dropped samples are still serialized
                assertEquals(
                    extra,
                    samples.count { sample ->
                        sample.threads == null && sample.code == ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED
                    }
                )
                assertEquals(
                    defaultLimit,
                    samples.count { sample ->
                        sample.threads != null && sample.code == ThreadBlockageSample.CODE_DEFAULT
                    }
                )
            }
        }
    }

    @Test
    fun testReachedAnrCaptureLimit() {
        with(rule) {
            repeat(rule.anrBehavior.anrPerSessionImpl) {
                stacktraceSampler.threadBlockageIntervals.add(ThreadBlockageInterval(0, samples = emptyList()))
                assertFalse(stacktraceSampler.reachedAnrStacktraceCaptureLimit())
            }

            stacktraceSampler.threadBlockageIntervals.add(ThreadBlockageInterval(0, samples = emptyList()))
            assertTrue(stacktraceSampler.reachedAnrStacktraceCaptureLimit())
        }
    }

    @Test
    fun testBelowAnrDurationThreshold() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                // if the lastTimeThreadUnblocked is zero this should never be true
                state.lastTargetThreadResponseMs = 0
                state.lastMonitorThreadResponseMs = 0
                assertFalse(blockedThreadDetector.isThreadBlockageThresholdExceeded(700))
                assertFalse(blockedThreadDetector.isThreadBlockageThresholdExceeded(70000))
            }
        }
    }

    @Test
    fun testAboveAnrDurationThreshold() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                // if the lastTimeThreadUnblocked is above the threshold return true
                val now = 100L
                state.lastTargetThreadResponseMs = now
                state.lastMonitorThreadResponseMs = now
                assertFalse(blockedThreadDetector.isThreadBlockageThresholdExceeded(now + 500))
                assertFalse(blockedThreadDetector.isThreadBlockageThresholdExceeded(now + 1000))
                assertTrue(blockedThreadDetector.isThreadBlockageThresholdExceeded(now + 1001))
                assertTrue(blockedThreadDetector.isThreadBlockageThresholdExceeded(now + 10000))
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
                assertTrue(blockedThreadDetector.isThreadBlockageThresholdExceeded(startTime + 500))

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(startTime, state.lastMonitorThreadResponseMs)

                // timestamp not updated if ANR threshold is met
                state.lastTargetThreadResponseMs = 0
                assertTrue(blockedThreadDetector.isThreadBlockageThresholdExceeded(startTime + 5000))

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(startTime, state.lastMonitorThreadResponseMs)

                // timestamp only updated if cached process threshold is reached
                assertFalse(blockedThreadDetector.isThreadBlockageThresholdExceeded(startTime + 60001))

                assertEquals(endTime, state.lastTargetThreadResponseMs)
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
            watchdogExecutorService.submit { blockedThreadDetector.updateThreadBlockageTracking(clock.now()) }
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
            populateIntervals()
            anrService.handleCrash("")
            val anrIntervals = anrService.getCapturedData()
            assertEquals(5, anrIntervals.size)
        }
    }

    private fun populateIntervals() {
        with(rule) {
            val state = stacktraceSampler
            state.threadBlockageIntervals.add(ThreadBlockageInterval(startTime = 14000000L))
            state.threadBlockageIntervals.add(ThreadBlockageInterval(startTime = 15000000L))
            state.threadBlockageIntervals.add(ThreadBlockageInterval(startTime = 15000500L))
            state.threadBlockageIntervals.add(ThreadBlockageInterval(startTime = 15001000L))
            state.threadBlockageIntervals.add(ThreadBlockageInterval(startTime = 16000000L))
        }
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
}
