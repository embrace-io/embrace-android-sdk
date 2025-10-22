package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class HucInstrumentationEnabledTest {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
                with(getSingleLogEnvelope().getLogOfType(EmbType.System.InternalError)) {
                    assertEquals(
                        "java.lang.reflect.InaccessibleObjectException",
                        checkNotNull(attributes).findAttributeValue("exception.type")
                    )
                }
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
}
