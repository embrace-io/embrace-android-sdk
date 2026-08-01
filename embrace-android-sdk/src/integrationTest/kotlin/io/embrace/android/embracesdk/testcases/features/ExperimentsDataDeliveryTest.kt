package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.opentelemetry.kotlin.getTracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExperimentsDataDeliveryTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.LogMessageWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.LogMessageWorker).blockingMode = false
        }
    }

    @Test
    fun `session part envelope with experiment records matches golden file`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    diskUsageCapture = false,
                    bgActivityCapture = true,
                ),
            ),
            testCaseAction = {
                recordSession {
                    val trackStartMs = clock.now()
                    embrace.trackExperiment(
                        embrace.createExperiment(id = "checkout-flow", startTimeMs = trackStartMs, variant = "variant-a"),
                    )

                    val flagStartMs = clock.tick()
                    embrace.trackFeatureFlag(embrace.createFeatureFlag(id = "dark-mode", startTimeMs = flagStartMs))

                    val untrackEndMs = clock.tick()
                    embrace.untrackExperiment("checkout-flow", endTimeMs = untrackEndMs)
                }
            },
            assertAction = {
                validatePayloadAgainstGoldenFile(getSingleSessionEnvelope(), "experiment_session_span.json")
            },
        )
    }

    @Test
    fun `log records carry the current experiment records`() {
        var trackStartMs: Long = -1
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    trackStartMs = clock.now()
                    embrace.trackExperiment(
                        TrackedExperiment(id = "checkout-flow", startTimeMs = trackStartMs, variant = "variant-a"),
                    )
                    embrace.logMessage("log with experiments", Severity.INFO)
                    flushLogBatch()
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Log)
                assertEquals(
                    "e:checkout-flow:variant-a:$trackStartMs",
                    log.attributes?.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS),
                )
            },
        )
    }

    @Test
    fun `cannot add experiments attribute on spans through public API`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val span = checkNotNull(embrace.startSpan("customer-span"))
                    span.addAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, "spoof")
                    span.stop()
                }
            },
            assertAction = {
                val spanAttrs = checkNotNull(getSingleSessionEnvelope().findSpanByName("customer-span").attributes)
                assertNull(spanAttrs.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS))
            },
        )
    }

    @Test
    fun `experiments attribute value is erased if set on a log`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logMessage(
                        "loggy log",
                        Severity.INFO,
                        mapOf(EmbCommonAttributes.EMB_EXPERIMENTS to "spoof"),
                    )
                    flushLogBatch()
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Log)
                assertEquals("", log.attributes?.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS))
            },
        )
    }

    @Test
    fun `cannot add experiments attribute on spans through the external tracer`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val tracer = embrace.getOpenTelemetryKotlin().getTracer("external-tracer")
                    val span = tracer.startSpan("external-span")
                    span.setStringAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, "spoof")
                    span.end()
                }
            },
            assertAction = {
                val spanAttrs = checkNotNull(getSingleSessionEnvelope().findSpanByName("external-span").attributes)
                assertNull(spanAttrs.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS))
            },
        )
    }

    private fun EmbraceActionInterface.flushLogBatch() {
        clock.tick(LOG_BATCH_FLUSH_MS)
        testRule.setup.getFakedWorkerExecutor(Worker.Background.LogMessageWorker).moveForwardAndRunBlocked(LOG_BATCH_FLUSH_MS)
    }

    private companion object {
        private const val LOG_BATCH_FLUSH_MS = 2000L
    }
}
