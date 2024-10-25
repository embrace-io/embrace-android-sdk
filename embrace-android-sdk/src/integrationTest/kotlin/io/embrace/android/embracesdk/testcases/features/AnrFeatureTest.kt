package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.injection.createAnrModule
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

private const val START_TIME_MS = 10000000000L
private const val INTERVAL_MS = 100L
private const val ANR_THRESHOLD_MS = 1000L
private const val MAX_SAMPLE_COUNT = 80
private const val MAX_INTERVAL_COUNT = 5
private const val SPAN_NAME = "emb-thread-blockage"

@RunWith(AndroidJUnit4::class)
internal class AnrFeatureTest {

    private lateinit var anrMonitorExecutor: BlockingScheduledExecutorService
    private lateinit var blockedThreadDetector: BlockedThreadDetector

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(currentTime = START_TIME_MS)
        val initModule = FakeInitModule(clock, FakeEmbLogger(throwOnInternalError = false))
        val workerThreadModule =
            FakeWorkerThreadModule(initModule, Worker.Background.AnrWatchdogWorker).apply {
                anrMonitorThread = AtomicReference(Thread.currentThread())
            }
        anrMonitorExecutor = workerThreadModule.executor.apply { blockingMode = false }
        val anrModule = createAnrModule(
            initModule,
            FakeConfigService(),
            workerThreadModule
        )
        blockedThreadDetector = anrModule.blockedThreadDetector

        EmbraceSetupInterface(
            currentTimeMs = START_TIME_MS,
            overriddenClock = clock,
            overriddenInitModule = initModule,
            overriddenWorkerThreadModule = workerThreadModule,
            fakeAnrModule = anrModule
        )
    }

    @Test
    fun `trigger ANRs`() {
        val firstSampleCount = 20
        val secondSampleCount = 10
        var secondAnrStartTime: Long? = null

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    triggerAnr(firstSampleCount)
                    secondAnrStartTime = clock.now()
                    triggerAnr(secondSampleCount)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                assertEquals(2, spans.size)
                assertAnrReceived(spans[0], START_TIME_MS, firstSampleCount)
                assertAnrReceived(spans[1], checkNotNull(secondAnrStartTime), secondSampleCount)
            }
        )
    }

    @Test
    fun `exceed max samples for one interval`() {
        val sampleCount = 100

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    triggerAnr(sampleCount)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                val span = spans.single()
                assertAnrReceived(span, START_TIME_MS, sampleCount)
            }
        )
    }

    @Test
    fun `exceed max intervals for one session`() {
        val initialSamples = 10
        val extraSamples = 5
        val intervalCount = 8
        val startTimes = mutableListOf<Long>()

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    repeat(intervalCount) { index ->
                        startTimes.add(clock.now())
                        triggerAnr(initialSamples + (index * extraSamples))
                    }
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                assertEquals(intervalCount, spans.size)

                repeat(intervalCount) { index ->
                    val span = spans[index]
                    val expectedSamples = initialSamples + (index * extraSamples)

                    // older intervals get dropped because they have fewer samples.
                    val intervalCode = when {
                        index < intervalCount - MAX_INTERVAL_COUNT -> "1"
                        else -> "0"
                    }
                    assertAnrReceived(span, startTimes[index], expectedSamples, intervalCode)
                }
            }
        )
    }

    @Test
    fun `in progress ANR added to payload`() {
        val sampleCount = 10
        var endTime: Long = -1

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    triggerAnr(sampleCount, incomplete = true)
                }
                endTime = clock.now()
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                val span = spans.single()
                assertAnrReceived(span, START_TIME_MS, sampleCount, endTime = endTime)
            }
        )
    }

    private fun assertAnrReceived(
        span: Span,
        startTime: Long,
        sampleCount: Int,
        expectedIntervalCode: String = "0",
        endTime: Long = startTime + ANR_THRESHOLD_MS + (sampleCount * INTERVAL_MS)
    ) {
        // assert span start/end times
        assertEquals(startTime, span.startTimeNanos?.nanosToMillis())
        assertEquals(endTime, span.endTimeNanos?.nanosToMillis())

        // assert span attributes
        span.attributes?.assertMatches {
            "emb.type" to "perf.thread_blockage"
            "interval_code" to expectedIntervalCode
        }

        val events = checkNotNull(span.events)

        events.forEachIndexed { index, event ->
            assertEquals("perf.thread_blockage_sample", event.name)

            // assert attributes
            event.attributes?.assertMatches {
                "emb.type" to "perf.thread_blockage_sample"
                "sample_overhead" to 0
                "sample_code" to  when {
                    index < MAX_SAMPLE_COUNT -> "0"
                    else -> "1"
                }
            }

            // assert interval time
            val expectedTime = startTime + ANR_THRESHOLD_MS + ((index + 1) * INTERVAL_MS)
            assertEquals(expectedTime, event.timestampNanos?.nanosToMillis())
        }
    }

    /**
     * Triggers an ANR by simulating the main thread getting blocked & unblocked. Time is controlled
     * with a fake Clock instance & a blockable executor that runs the blockage checks.
     */
    private fun EmbraceActionInterface.triggerAnr(
        sampleCount: Int,
        intervalMs: Long = INTERVAL_MS,
        incomplete: Boolean = false
    ) {
        with(anrMonitorExecutor) {
            blockingMode = true

            // increase time by initial delay, and kick off check that marks thread as blocked
            moveForwardAndRunBlocked(0)
            moveForwardAndRunBlocked(ANR_THRESHOLD_MS)

            // run the thread block check & create a sample
            repeat(sampleCount) {
                moveForwardAndRunBlocked(intervalMs)
            }

            if (!incomplete) {
                // simulate the main thread becoming responsive again, ending the ANR interval
                blockedThreadDetector.onTargetThreadResponse(clock.now())
            }

            // AnrService#getCapturedData() currently gets a Callable with a timeout, so we
            // need to allow all jobs on the executor to finish running for spans to be
            // included in the payload.
            blockingMode = false
            runCurrentlyBlocked()
        }
    }

    private fun Envelope<SessionPayload>.findAnrSpans() = checkNotNull(data.spans?.filter { it.name == SPAN_NAME })
}
