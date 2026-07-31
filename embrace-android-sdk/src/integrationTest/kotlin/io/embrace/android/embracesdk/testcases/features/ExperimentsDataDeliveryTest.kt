package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.getTracer
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExperimentsDataDeliveryTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
                        TrackedExperiment(id = "checkout-flow", startTimeMs = trackStartMs, variant = "variant-a"),
                    )

                    val flagStartMs = clock.tick()
                    embrace.trackFeatureFlag(TrackedFeatureFlag(id = "dark-mode", startTimeMs = flagStartMs))

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
    fun `experiments attribute not on session part span when API not used`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val attrs = checkNotNull(getSingleSessionEnvelope().findSessionPartSpan().attributes)
                assertNull(attrs.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS))
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
}
