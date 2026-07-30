package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that the telemetry exported for common tracing & logging operations is a 1:1 match between
 * the opentelemetry-kotlin 'compat' implementation and the 'KMP' implementation.
 */
@RunWith(AndroidJUnit4::class)
internal class OtelExportParityTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `trace export matches golden file using compat implementation`() {
        assertTraceExport(useKotlinSdk = false)
    }

    @Test
    fun `trace export matches golden file using kmp implementation`() {
        assertTraceExport(useKotlinSdk = true)
    }

    @Test
    fun `log export matches golden file using compat implementation`() {
        assertLogExport(useKotlinSdk = false)
    }

    @Test
    fun `log export matches golden file using kmp implementation`() {
        assertLogExport(useKotlinSdk = true)
    }

    private fun assertTraceExport(useKotlinSdk: Boolean) {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig(useKotlinSdk),
            testCaseAction = {
                recordSession {
                    embrace.startSpan(SPAN_NAME).apply {
                        addAttribute("my-attribute", "my-value")
                        addEvent("my-event")
                    }.stop()
                }
            },
            otelExportAssertion = {
                assertSpansMatchParityGoldenFile(
                    spans = awaitSpans(1) { it.name == SPAN_NAME },
                    goldenFile = "otel-export-parity-trace.json",
                )
            },
        )
    }

    private fun assertLogExport(useKotlinSdk: Boolean) {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig(useKotlinSdk),
            testCaseAction = {
                recordSession {
                    embrace.logMessage(LOG_MESSAGE, Severity.WARNING, mapOf("my-attribute" to "my-value"))
                }
            },
            otelExportAssertion = {
                assertLogsMatchParityGoldenFile(
                    logs = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.Log.key) },
                    goldenFile = "otel-export-parity-log.json",
                )
            },
        )
    }

    /**
     * Selects the implementation under test.
     */
    private fun instrumentedConfig(useKotlinSdk: Boolean) = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(otelKotlinSdkEnabled = useKotlinSdk)
    )

    private companion object {
        private const val SPAN_NAME = "test-span"
        private const val LOG_MESSAGE = "test message"
    }
}
