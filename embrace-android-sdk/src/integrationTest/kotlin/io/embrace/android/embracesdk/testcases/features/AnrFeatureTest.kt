package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val INTERVAL_MS = 100L
private const val ANR_THRESHOLD_MS = 1000L
private const val MAX_SAMPLE_COUNT = 80
private const val MAX_INTERVAL_COUNT = 5
private const val SPAN_NAME = "emb-thread-blockage"

@RunWith(AndroidJUnit4::class)
internal class AnrFeatureTest {

    private lateinit var anrMonitorExecutor: BlockingScheduledExecutorService
    private var startTimeMs: Long = 0L

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workerToFake = Worker.Background.AnrWatchdogWorker,
            anrMonitoringThread = Thread.currentThread()
        ).also {
            with(it) {
                anrMonitorExecutor = getFakedWorkerExecutor()
                anrMonitorExecutor.blockingMode = true
            }
        }
    }

    @Test
    fun `trigger ANRs`() {
        val firstSampleCount = 20
        val secondSampleCount = 10
        var secondAnrStartTime: Long? = null

        testRule.runTest(
            testCaseAction = {
                startTimeMs = recordSession {
                    triggerAnr(firstSampleCount)
                    secondAnrStartTime = clock.now()
                    triggerAnr(secondSampleCount)
                }.actionTimeMs
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                assertEquals(2, spans.size)
                assertAnrReceived(spans[0], startTimeMs, firstSampleCount)
                assertAnrReceived(spans[1], checkNotNull(secondAnrStartTime), secondSampleCount)
            },
            otelExportAssertion = {
                awaitSpansWithType(2, EmbType.Performance.ThreadBlockage)
            }
        )
    }

    @Test
    fun `exceed max samples for one interval`() {
        val sampleCount = 100

        testRule.runTest(
            testCaseAction = {
                startTimeMs = recordSession {
                    triggerAnr(sampleCount)
                }.actionTimeMs
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                val span = spans.single()
                assertAnrReceived(span, startTimeMs, sampleCount)
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
                }.let {
                    startTimeMs = it.actionTimeMs
                    endTime = it.endTimeMs
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()

                // assert ANRs received
                val spans = message.findAnrSpans()
                val span = spans.single()
                assertAnrReceived(span, startTimeMs, sampleCount, endTime = endTime)
            }
        )
    }

    private fun assertAnrReceived(
        span: Span,
        startTime: Long,
        sampleCount: Int,
        expectedIntervalCode: String = "0",
        endTime: Long = startTime + ANR_THRESHOLD_MS + (sampleCount * INTERVAL_MS),
    ) {
        // assert span start/end times
        assertEquals(startTime, span.startTimeNanos?.nanosToMillis())
        assertEquals(endTime, span.endTimeNanos?.nanosToMillis())

        // assert span attributes
        span.attributes?.assertMatches(
            mapOf(
                "emb.type" to "perf.thread_blockage",
                "interval_code" to expectedIntervalCode
            )
        )

        val events = checkNotNull(span.events)

        events.forEachIndexed { index, event ->
            assertEquals("perf.thread_blockage_sample", event.name)

            // assert attributes
            event.attributes?.assertMatches(
                mapOf(
                    "emb.type" to "perf.thread_blockage_sample",
                    "sample_overhead" to 0,
                    "sample_code" to when {
                        index < MAX_SAMPLE_COUNT -> "0"
                        else -> "1"
                    }
                )
            )

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
        incomplete: Boolean = false,
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
            val blockedThreadDetector = testRule.bootstrapper.anrModule.blockedThreadDetector

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
