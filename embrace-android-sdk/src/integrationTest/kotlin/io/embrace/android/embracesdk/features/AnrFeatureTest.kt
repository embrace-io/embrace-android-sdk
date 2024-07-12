package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.injection.AnrModuleImpl
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.internal.worker.WorkerName
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        val initModule = FakeInitModule(clock)
        val workerThreadModule =
            FakeWorkerThreadModule(initModule, WorkerName.ANR_MONITOR).apply {
                anrMonitorThread = AtomicReference(Thread.currentThread())
            }
        anrMonitorExecutor = workerThreadModule.executor
        val anrModule = AnrModuleImpl(
            initModule,
            FakeEssentialServiceModule(),
            workerThreadModule,
            FakeOpenTelemetryModule()
        )
        blockedThreadDetector = anrModule.blockedThreadDetector

        IntegrationTestRule.Harness(
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

        with(testRule.harness) {
            var secondAnrStartTime: Long? = null
            val message = recordSession {
                triggerAnr(firstSampleCount)
                secondAnrStartTime = overriddenClock.now()
                triggerAnr(secondSampleCount)
            }
            checkNotNull(message)

            // assert ANRs received
            val spans = message.findAnrSpans()
            assertEquals(2, spans.size)
            assertAnrReceived(spans[0], START_TIME_MS, firstSampleCount)
            assertAnrReceived(spans[1], checkNotNull(secondAnrStartTime), secondSampleCount)
        }
    }

    @Test
    fun `exceed max samples for one interval`() {
        val sampleCount = 100

        with(testRule.harness) {
            val message = recordSession {
                triggerAnr(sampleCount)
            }
            checkNotNull(message)

            // assert ANRs received
            val spans = message.findAnrSpans()
            val span = spans.single()
            assertAnrReceived(span, START_TIME_MS, sampleCount)
        }
    }

    @Test
    fun `exceed max intervals for one session`() {
        val initialSamples = 10
        val extraSamples = 5
        val intervalCount = 8
        val startTimes = mutableListOf<Long>()

        with(testRule.harness) {
            val message = recordSession {
                repeat(intervalCount) { index ->
                    startTimes.add(overriddenClock.now())
                    triggerAnr(initialSamples + (index * extraSamples))
                }
            }
            checkNotNull(message)

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
    }

    @Test
    fun `in progress ANR added to payload`() {
        val sampleCount = 10

        with(testRule.harness) {
            val message = recordSession {
                triggerAnr(sampleCount, incomplete = true)
            }
            checkNotNull(message)

            // assert ANRs received
            val spans = message.findAnrSpans()
            val span = spans.single()
            assertAnrReceived(span, START_TIME_MS, sampleCount, endTime = testRule.harness.overriddenClock.now())
        }
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
        val attributes = checkNotNull(span.attributes)
        assertEquals(expectedIntervalCode, attributes.findAttributeValue("interval_code"))
        assertEquals("perf.thread_blockage", attributes.findAttributeValue("emb.type"))

        val events = checkNotNull(span.events)

        events.forEachIndexed { index, event ->
            assertEquals("perf.thread_blockage_sample", event.name)

            // assert attributes
            val attrs = checkNotNull(event.attributes)
            assertEquals("perf.thread_blockage_sample", attrs.findAttributeValue("emb.type"))
            assertEquals("0", attrs.findAttributeValue("sample_overhead"))

            val expectedCode = when {
                index < MAX_SAMPLE_COUNT -> "0"
                else -> "1"
            }
            assertEquals(expectedCode, attrs.findAttributeValue("sample_code"))

            // assert interval time
            val expectedTime = startTime + ANR_THRESHOLD_MS + ((index + 1) * INTERVAL_MS)
            assertEquals(expectedTime, event.timestampNanos?.nanosToMillis())
        }
    }

    /**
     * Triggers an ANR by simulating the main thread getting blocked & unblocked. Time is controlled
     * with a fake Clock instance & a blockable executor that runs the blockage checks.
     */
    private fun IntegrationTestRule.Harness.triggerAnr(
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
                blockedThreadDetector.onTargetThreadResponse(overriddenClock.now())
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