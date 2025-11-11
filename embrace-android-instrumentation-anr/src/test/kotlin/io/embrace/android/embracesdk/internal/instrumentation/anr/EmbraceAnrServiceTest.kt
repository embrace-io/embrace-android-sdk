package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeBlockedThreadListener
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrSample
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrSampleList
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

internal class EmbraceAnrServiceTest {
    private lateinit var anrExecutorService: SingleThreadTestScheduledExecutor

    @Rule
    @JvmField
    val rule = EmbraceAnrServiceRule(
        scheduledExecutorSupplier = {
            anrExecutorService = SingleThreadTestScheduledExecutor()
            anrExecutorService
        }
    )

    @Before
    fun setUp() {
        anrExecutorService.reset()
        anrExecutorService.submit { rule.anrMonitorThread.set(currentThread()) }
            .get(1L, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        anrExecutorService.shutdown()
        anrExecutorService.awaitTermination(1L, TimeUnit.SECONDS)
        assertFalse(anrExecutorService.executing.get())
    }

    @Test
    fun testFinishInitialization() {
        with(rule) {
            val configService = FakeConfigService()
            anrExecutorService.submit {
                anrService.startAnrCapture()
            }.get(1L, TimeUnit.SECONDS)
            // verify the config service was changed from the bootstrapped early version
            assertNotSame(this.fakeConfigService, configService)
        }
    }

    @Test
    fun testListener() {
        with(rule) {
            val listener = FakeBlockedThreadListener()
            anrService.addBlockedThreadListener(listener)

            // Test that the listener actually gets notified when thread blocking events occur
            anrService.onThreadBlocked(currentThread(), 1000L)
            assertEquals(1, listener.blockedCount)

            anrService.onThreadUnblocked(currentThread(), 2000L)
            assertEquals(1, listener.unblockedCount)
        }
    }

    @Test
    fun testColdStartIgnored() {
        with(rule) {
            // cold starts should always be ignored
            state.lastTargetThreadResponseMs = 1
            anrService.onForeground()

            // assert no ANR interval was added
            assertEquals(0, stacktraceSampler.anrIntervals.size)
        }
    }

    @Test
    fun testCleanCollections() {
        with(rule) {
            // assert the ANR interval was added
            val anrIntervals = stacktraceSampler.anrIntervals
            anrIntervals.add(AnrInterval(startTime = 15000000, endTime = 15000100))
            val inProgressInterval = AnrInterval(startTime = 15000000, lastKnownTime = 15000100)
            anrIntervals.add(inProgressInterval)
            assertEquals(2, anrIntervals.size)

            // the ANR interval should be removed here
            anrService.cleanCollections()
            anrExecutorService.shutdown()
            anrExecutorService.awaitTermination(1, TimeUnit.SECONDS)
            assertEquals(1, anrIntervals.size)
            assertEquals(inProgressInterval, anrIntervals.single())
        }
    }

    @Test
    fun testGetIntervals() {
        with(rule) {
            populateAnrIntervals()

            val anrIntervals = anrService.getCapturedData()
            assertEquals(5, anrIntervals.size)
            assertEquals(14000000L, anrIntervals[0].startTime)
            assertEquals(15000000L, anrIntervals[1].startTime)
            assertEquals(15000500L, anrIntervals[2].startTime)
            assertEquals(15001000L, anrIntervals[3].startTime)
            assertEquals(16000000L, anrIntervals[4].startTime)
        }
    }

    @Test
    fun testGetIntervalsAnrInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            blockedThreadDetector.listener = anrService
            state.anrInProgress = true

            // assert only one anr interval was added from the anrInProgress flag
            val anrIntervals = anrService.getCapturedData()
            val interval = anrIntervals.single()
            assertEquals(0, interval.startTime)
            assertNull(interval.endTime)
            assertEquals(500L, interval.lastKnownTime)
            assertEquals(AnrInterval.Type.UI, interval.type)
            assertNotNull(interval.anrSampleList)
        }
    }

    @Test
    fun testGetIntervalsCrashInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            blockedThreadDetector.listener = anrService
            state.anrInProgress = true

            // assert only one anr interval was added from the anrInProgress flag
            val anrIntervals = anrService.getCapturedData()
            assertEquals(1, anrIntervals.size)
        }
    }

    @Test
    fun testGetIntervalsWithStacktraces() {
        with(rule) {
            // create an ANR service with one stacktrace
            clock.setCurrentTime(15020000L)

            blockedThreadDetector.listener = anrService
            state.anrInProgress = true
            state.lastTargetThreadResponseMs = 15000000L
            anrService.onThreadBlockedInterval(currentThread(), clock.now())
            assertEquals(1, stacktraceSampler.size())

            // assert only one anr interval was added from the anrInProgress flag
            val anrIntervals = anrService.getCapturedData()
            val interval = anrIntervals.single()
            assertEquals(15000000L, interval.startTime)
            assertNull(interval.endTime)
            assertEquals(15020000L, interval.lastKnownTime)
            assertEquals(AnrInterval.Type.UI, interval.type)

            val stacktraces = interval.anrSampleList
            assertNotNull(stacktraces)
            val tick = checkNotNull(stacktraces?.samples?.single())
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

                blockedThreadDetector.updateAnrTracking(anrStartTs)
                state.lastTargetThreadResponseMs = anrStartTs
                blockedThreadDetector.onTargetThreadResponse(anrStartTs)
                assertFalse(state.anrInProgress)

                blockedThreadDetector.updateAnrTracking(
                    anrInProgressTs
                )
                assertTrue(state.anrInProgress)

                state.lastTargetThreadResponseMs = anrEndTs
                blockedThreadDetector.onTargetThreadResponse(anrEndTs)
                assertFalse(state.anrInProgress)

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
            targetThreadHandler.onIdleThread()
            val intervals = anrService.getCapturedData()
            assertEquals(0, intervals.size)
        }
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
            targetThreadHandler.onIdleThread()
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
                    anrService.onThreadBlockedInterval(currentThread(), clock.now())
                }
                anrService.onThreadUnblocked(currentThread(), clock.now())

                assertEquals(1, stacktraceSampler.anrIntervals.size)

                val interval = checkNotNull(stacktraceSampler.anrIntervals.first())
                val samples = checkNotNull(interval.anrSampleList).samples
                assertEquals(count, samples.size)

                // after the default limit, samples are dropped samples are still serialized
                assertEquals(
                    extra,
                    samples.count { sample ->
                        sample.threads == null && sample.code == AnrSample.CODE_SAMPLE_LIMIT_REACHED
                    }
                )
                assertEquals(
                    defaultLimit,
                    samples.count { sample ->
                        sample.threads != null && sample.code == AnrSample.CODE_DEFAULT
                    }
                )
            }
        }
    }

    @Test
    fun testProcessAnrTickDisabled() {
        with(rule) {
            // create an ANR service with config that disables ANR capture
            rule.anrBehavior.anrCaptureEnabled = false
            clock.setCurrentTime(15020000L)
            anrService.onThreadBlockedInterval(currentThread(), clock.now())
            assertEquals(0, stacktraceSampler.size())

            // assert no anr intervals were added
            val anrIntervals = anrService.getCapturedData()
            assertTrue(anrIntervals.isEmpty())
        }
    }

    @Test
    fun testReachedAnrCaptureLimit() {
        with(rule) {
            rule.anrBehavior.anrPerSessionImpl = 3
            assertFalse(stacktraceSampler.reachedAnrStacktraceCaptureLimit())

            stacktraceSampler.anrIntervals.add(AnrInterval(0, anrSampleList = AnrSampleList(listOf())))
            stacktraceSampler.anrIntervals.add(AnrInterval(0, anrSampleList = AnrSampleList(listOf())))
            stacktraceSampler.anrIntervals.add(AnrInterval(0, anrSampleList = AnrSampleList(listOf())))
            assertFalse(stacktraceSampler.reachedAnrStacktraceCaptureLimit())

            stacktraceSampler.anrIntervals.add(AnrInterval(0, anrSampleList = AnrSampleList(listOf())))
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
                assertFalse(blockedThreadDetector.isAnrDurationThresholdExceeded(700))
                assertFalse(blockedThreadDetector.isAnrDurationThresholdExceeded(70000))
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
                assertFalse(blockedThreadDetector.isAnrDurationThresholdExceeded(now + 500))
                assertFalse(blockedThreadDetector.isAnrDurationThresholdExceeded(now + 1000))
                assertTrue(blockedThreadDetector.isAnrDurationThresholdExceeded(now + 1001))
                assertTrue(blockedThreadDetector.isAnrDurationThresholdExceeded(now + 10000))
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
                assertTrue(blockedThreadDetector.isAnrDurationThresholdExceeded(startTime + 500))

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(startTime, state.lastMonitorThreadResponseMs)

                // timestamp not updated if ANR threshold is met
                state.lastTargetThreadResponseMs = 0
                assertTrue(blockedThreadDetector.isAnrDurationThresholdExceeded(startTime + 5000))

                assertEquals(0, state.lastTargetThreadResponseMs)
                assertEquals(startTime, state.lastMonitorThreadResponseMs)

                // timestamp only updated if cached process threshold is reached
                assertFalse(blockedThreadDetector.isAnrDurationThresholdExceeded(startTime + 60001))

                assertEquals(endTime, state.lastTargetThreadResponseMs)
                assertEquals(endTime, state.lastMonitorThreadResponseMs)
            }
        }
    }

    @Test
    fun `check ANR recovery`() {
        with(rule) {
            clock.setCurrentTime(100000L)
            targetThreadHandler.onIdleThread()
            clock.tick(2000L)
            anrExecutorService.submit { blockedThreadDetector.updateAnrTracking(clock.now()) }
                .get(1L, TimeUnit.SECONDS)
            targetThreadHandler.onIdleThread()
            assertFalse(state.anrInProgress)
        }
    }

    @Test
    fun `test handleCrash stops ANR tracking but samples can still be retrieved`() {
        with(rule) {
            clock.setCurrentTime(14000000L)
            rule.anrBehavior.bgAnrCaptureEnabled = true
            anrService.onForeground()
            anrExecutorService.submit {
                assertTrue(state.started.get())
            }
            populateAnrIntervals()
            anrService.handleCrash("")
            val anrIntervals = anrService.getCapturedData()
            assertEquals(5, anrIntervals.size)
            assertFalse(state.started.get())
        }
    }

    private fun populateAnrIntervals() {
        with(rule) {
            val state = stacktraceSampler
            state.anrIntervals.add(AnrInterval(startTime = 14000000L))
            state.anrIntervals.add(AnrInterval(startTime = 15000000L))
            state.anrIntervals.add(AnrInterval(startTime = 15000500L))
            state.anrIntervals.add(AnrInterval(startTime = 15001000L))
            state.anrIntervals.add(AnrInterval(startTime = 16000000L))
        }
    }

    /**
     * Some tests require running functions meant to be run in the ANR monitoring thread synchronously on the current thread to simulate
     * conditions that are to be tested. This allows the [enforceThread] to not fail by temporarily switching out the thread to be
     * compared against.
     */
    private fun executeSynchronouslyOnCurrentThread(action: () -> Unit) {
        with(rule) {
            synchronized(anrMonitorThread) {
                val previousAnrMonitoringThread = anrMonitorThread.get()
                anrMonitorThread.set(currentThread())
                action()
                anrMonitorThread.set(previousAnrMonitoringThread)
            }
        }
    }
}
