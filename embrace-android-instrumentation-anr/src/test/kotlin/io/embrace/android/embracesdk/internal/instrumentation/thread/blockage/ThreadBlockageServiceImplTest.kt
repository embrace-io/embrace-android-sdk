package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.UNBLOCKED
import io.embrace.android.embracesdk.internal.payload.Span
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
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

internal class ThreadBlockageServiceImplTest {

    private lateinit var watchdogExecutorService: SingleThreadTestScheduledExecutor

    @Rule
    @JvmField
    val rule = ThreadBlockageServiceRule(
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
                service.startCapture()
            }.get(1L, TimeUnit.SECONDS)
            // verify the config service was changed from the bootstrapped early version
            assertNotSame(this.fakeConfigService, configService)
        }
    }

    @Test
    fun testCleanCollections() {
        with(rule) {
            // assert the interval was added
            createThreadBlockageInterval()

            // create in progress interval
            stacktraceSampler.onThreadBlockageEvent(BLOCKED, clock.now())
            stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())

            val intervals = stacktraceSampler.getThreadBlockageIntervals()
            assertEquals(2, intervals.size)

            // the interval should be removed here
            service.onPostSessionChange()
            watchdogExecutorService.shutdown()
            watchdogExecutorService.awaitTermination(1, TimeUnit.SECONDS)
            assertEquals(1, stacktraceSampler.getThreadBlockageIntervals().size)
        }
    }

    @Test
    fun testGetIntervals() {
        with(rule) {
            repeat(5) {
                createThreadBlockageInterval()
            }

            val intervals = service.snapshotSpans()
            assertEquals(5, intervals.size)
        }
    }

    @Test
    fun testGetIntervalsWhileThreadBlocked() {
        with(rule) {
            clock.setCurrentTime(500)
            createThreadBlockageInterval(complete = false)

            val intervals = service.snapshotSpans()
            val interval = intervals.single()
            assertEquals(500L, interval.startTimeNanos?.nanosToMillis())
            assertEquals(500L, interval.endTimeNanos?.nanosToMillis())
            assertEquals(500L, interval.lastKnownTime?.nanosToMillis())
        }
    }

    @Test
    fun testGetIntervalsCrashInProgress() {
        with(rule) {
            clock.setCurrentTime(500)
            createThreadBlockageInterval(complete = false)

            val intervals = service.snapshotSpans()
            assertEquals(1, intervals.size)
        }
    }

    @Test
    fun testGetIntervalsWithStacktraces() {
        with(rule) {
            clock.setCurrentTime(15020000L)

            stacktraceSampler.onThreadBlockageEvent(BLOCKED, clock.now())
            stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
            assertEquals(1, stacktraceSampler.getThreadBlockageIntervals().size)

            // assert only one interval was added
            val intervals = service.snapshotSpans()
            val interval = intervals.single()
            assertEquals(15020000L, interval.startTimeNanos?.nanosToMillis())
            assertEquals(15020000L, interval.endTimeNanos?.nanosToMillis())
            assertEquals(15020000L, interval.lastKnownTime?.nanosToMillis())

            val tick = checkNotNull(interval.events?.single())
            assertEquals(15020000L, tick.timestampNanos?.nanosToMillis())
            assertNotNull(tick.attributes?.find { it.key == ExceptionAttributes.EXCEPTION_STACKTRACE })
        }
    }

    @Test
    fun testIntervalStartAndEndTimes() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                val startTs = 15020000L
                val inProgressTs = 15021500L
                val endTs = 15023000L
                clock.setCurrentTime(startTs)

                blockedThreadDetector.onMonitorThreadInterval(startTs)
                blockedThreadDetector.onTargetThreadProcessedMessage(startTs)
                blockedThreadDetector.onMonitorThreadInterval(inProgressTs)
                blockedThreadDetector.onTargetThreadProcessedMessage(endTs)

                val intervals = service.snapshotSpans()
                assertEquals(1, intervals.size)
                val interval = intervals[0]
                assertEquals(startTs, interval.startTimeNanos?.nanosToMillis())
                assertEquals(endTs, interval.endTimeNanos?.nanosToMillis())
                assertNull(interval.lastKnownTime)
            }
        }
    }

    @Test
    fun `test state is reset when onForeground is executed to prevent false positive`() {
        val startTs = 15020000L
        val endTs = 15023000L
        with(rule) {
            clock.setCurrentTime(startTs)
            service.onForeground()
            service.onBackground()
            clock.setCurrentTime(endTs)
            service.onForeground()
            // Since Looper is a mock, we execute this operation to
            // ensure onMainThreadUnblocked runs and lastTargetThreadResponseMs gets updated
            simulateMainThreadRecovery()
            val intervals = service.snapshotSpans()
            assertEquals(0, intervals.size)
        }
    }

    private fun ThreadBlockageServiceRule<SingleThreadTestScheduledExecutor>.simulateMainThreadRecovery() {
        blockedThreadDetector.onTargetThreadProcessedMessage(clock.now())
    }

    @Test
    fun `test timestamps are updated if onMainThreadUnblocked runs before onMonitorThreadHeartbeat to prevent false positive`() {
        val startTs = 15020000L
        val endTs = 15023000L

        with(rule) {
            clock.setCurrentTime(startTs)
            service.onForeground()
            service.onBackground()
            clock.setCurrentTime(endTs)
            service.onForeground()
            simulateMainThreadRecovery()
            val intervals = service.snapshotSpans()
            assertEquals(0, intervals.size)
        }
    }

    @Test
    fun testIntervalCaptureLimit() {
        executeSynchronouslyOnCurrentThread {
            // create a service with one stacktrace
            with(rule) {
                clock.setCurrentTime(15020000L)
                val defaultLimit = 80
                val extra = 10
                val count = defaultLimit + extra

                stacktraceSampler.onThreadBlockageEvent(BLOCKED, clock.now())

                repeat(count) {
                    stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())
                }
                stacktraceSampler.onThreadBlockageEvent(UNBLOCKED, clock.now())

                assertEquals(1, stacktraceSampler.getThreadBlockageIntervals().size)

                val interval = checkNotNull(stacktraceSampler.getThreadBlockageIntervals().first())
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
                        sample.code == ThreadBlockageSample.CODE_DEFAULT
                    }
                )
            }
        }
    }

    @Test
    fun testReachedIntervalCaptureLimit() {
        with(rule) {
            val limit = rule.behavior.intervalsPerSessionImpl
            repeat(limit) { count ->
                createThreadBlockageInterval()
                assertEquals(count + 1, stacktraceSampler.getThreadBlockageIntervals().size)
            }
            createThreadBlockageInterval()
            assertEquals(5, stacktraceSampler.getThreadBlockageIntervals().filter { it.samples != null }.size)
            assertEquals(1, stacktraceSampler.getThreadBlockageIntervals().filter { it.samples == null }.size)
        }
    }

    @Test
    fun testBelowThreadBlockageThreshold() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                clock.setCurrentTime(0)
                blockedThreadDetector.onMonitorThreadInterval(700)
                blockedThreadDetector.onMonitorThreadInterval(70000)
                assertTrue(stacktraceSampler.getThreadBlockageIntervals().isEmpty())
            }
        }
    }

    @Test
    fun testAboveThreadBlockageThreshold() {
        executeSynchronouslyOnCurrentThread {
            with(rule) {
                val now = 100L
                recreateService()

                clock.setCurrentTime(now + 500)
                blockedThreadDetector.onMonitorThreadInterval(now + 500)

                clock.setCurrentTime(now + 1000)
                blockedThreadDetector.onMonitorThreadInterval(now + 1000)

                clock.setCurrentTime(now + 2001)
                blockedThreadDetector.onMonitorThreadInterval(now + 2001)

                clock.setCurrentTime(now + 10000)
                blockedThreadDetector.onMonitorThreadInterval(now + 10000)

                val samples = checkNotNull(stacktraceSampler.getThreadBlockageIntervals().single().samples)
                assertEquals(3, samples.size)
                assertEquals(now + 1000, samples[0].timestamp)
                assertEquals(now + 2001, samples[1].timestamp)
                assertEquals(now + 10000, samples[2].timestamp)
            }
        }
    }

    @Test
    fun `test handleCrash stops tracking but samples can still be retrieved`() {
        with(rule) {
            clock.setCurrentTime(14000000L)
            service.onForeground()
            repeat(5) {
                createThreadBlockageInterval()
            }
            service.handleCrash("")
            val intervals = service.snapshotSpans()
            assertEquals(5, intervals.size)
        }
    }

    private fun ThreadBlockageServiceRule<*>.createThreadBlockageInterval(duration: Long = 1000, complete: Boolean = true) {
        stacktraceSampler.onThreadBlockageEvent(BLOCKED, clock.now())
        stacktraceSampler.onThreadBlockageEvent(BLOCKED_INTERVAL, clock.now())

        if (complete) {
            clock.tick(duration)
            stacktraceSampler.onThreadBlockageEvent(UNBLOCKED, clock.now())
        }
    }

    /**
     * Some tests require running functions meant to be run in the monitoring thread synchronously on the current thread to simulate
     * conditions that are to be tested.
     */
    private fun executeSynchronouslyOnCurrentThread(action: () -> Unit) {
        with(rule) {
            synchronized(watchdogMonitorThread) {
                val previousWatchdogThread = watchdogMonitorThread.get()
                watchdogMonitorThread.set(currentThread())
                action()
                watchdogMonitorThread.set(previousWatchdogThread)
            }
        }
    }

    private val Span.lastKnownTime: Long?
        get() = attributes?.find { it.key == "last_known_time_unix_nano" }?.data?.toLong()
}
