package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the SDK can export OpenTelemetry spans without sending any data to Embrace.
 */
@RunWith(AndroidJUnit4::class)
internal class OtelExportOnlyTest {

    private val otelOnlyConfig = FakeInstrumentedConfig(
        project = FakeProjectConfig(appId = null),
        enabledFeatures = FakeEnabledFeatureConfig(otelExportOnly = true)
    )

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `only export OTel`() {
        testRule.runTest(
            instrumentedConfig = otelOnlyConfig,
            testCaseAction = {
                recordSession {
                    embrace.startSpan("test-span")?.stop()
                    embrace.logInfo("test-log")
                }
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
                assertEquals(0, getLogEnvelopes(0).size)
            },
            otelExportAssertion = {
                // span exported
                val span = awaitSpanExport(1) {
                    it.name == "test-span"
                }.single()
                assertEquals("test-span", span.name)

                // log exported
                val log = awaitLogExport(1) {
                    true
                }.single()
                assertEquals("test-log", log.bodyValue?.value)
            }
        )
    }
}
