package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class HucLiteInstrumentationEnabledTest {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `sdk starts successfully but internal error logged when HUC Lite instrumentation enabled`() {
        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
            },
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    hucLiteInstrumentation = true,
                )
            ),
            testCaseAction = {
                assertTrue(embrace.isStarted)
                recordSession()
            },
            assertAction = {
                getSingleLogEnvelope()
                    .getLogOfType(EmbType.System.InternalError)
                    .assertHucLiteInitializationAttempt(true)
                assertNotNull(getSingleSessionEnvelope())
            },
        )
    }

    @Test
    fun `sdk starts successfully with no internal error when HUC Lite instrumentation disabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    hucLiteInstrumentation = false,
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

    @Test
    fun `SDK starts when both HUC instrumentations are enabled`() {
        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
            },
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    hucLiteInstrumentation = true,
                    httpUrlConnectionCapture = true,
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

    private fun Log.assertHucLiteInitializationAttempt(attempted: Boolean) {
        val attrs = checkNotNull(attributes)
        assertEquals(
            "java.lang.reflect.InaccessibleObjectException",
            attrs.findAttributeValue("exception.type")
        )
        assertEquals(
            attempted,
            attrs.findAttributeValue("exception.stacktrace")?.contains(HucLiteDataSource::class.java.name)
        )
    }
}
