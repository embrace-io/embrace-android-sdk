package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.getLogsOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class HucInstrumentationEnabledTest(
    private val otelSdkMode: OtelSdkMode,
) {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `sdk starts successfully but internal error logged when HUC instrumentation enabled`() {
        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
            },
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    httpUrlConnectionCapture = true
                )
            ),
            testCaseAction = {
                assertTrue(embrace.isStarted)
                recordSession()
            },
            assertAction = {
                // URL's stream handler factory is JVM-global, so whichever mode runs second also logs
                // 'factory already defined' from the fallback registration.
                val errorTypes = getSingleLogEnvelope().getLogsOfType(EmbType.System.InternalError).map {
                    checkNotNull(it.attributes).findAttributeValue(ExceptionAttributes.EXCEPTION_TYPE)
                }
                assertEquals("java.lang.reflect.InaccessibleObjectException", errorTypes.first())
                assertNotNull(getSingleSessionEnvelope())
            },
        )
    }

    @Test
    fun `sdk starts successfully with no internal error when HUC disabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    httpUrlConnectionCapture = false
                )
            ),
            testCaseAction = {
                assertTrue(embrace.isStarted)
                recordSession()
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
            },
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
