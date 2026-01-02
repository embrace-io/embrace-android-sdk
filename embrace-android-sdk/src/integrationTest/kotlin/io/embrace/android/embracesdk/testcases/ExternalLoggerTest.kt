package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.getLogger
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class ExternalLoggerTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val instrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            bgActivityCapture = true
        )
    )

    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var embLogger: Logger

    private val remoteConfig = RemoteConfig(
        otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100.0f) // Enable Kotlin SDK
    )

    @Before
    fun setup() {
        logExporter = FakeLogRecordExporter()
    }

    @Test
    fun `record a log with otel logging API during a session`() {
        var logTime: Long = -1L
        var observedTime: Long = -1L
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeLogger()
                observedTime = clock.now().millisToNanos()
                clock.tick()
                recordSession {
                    logTime = clock.now().millisToNanos()
                    embLogger.log(
                        body = "test",
                        timestamp = logTime,
                        observedTimestamp = observedTime,
                        context = null,
                        severityNumber = SeverityNumber.FATAL,
                        severityText = "DANG",
                    ) {
                        setStringAttribute("foo", "bar")
                    }
                    clock.tick(2000L)
                }
            },
            assertAction = {
                val logEnvelope = getSingleLogEnvelope()
                val logRecord = logEnvelope.getLastLog()
                logRecord.assertLogRecord(
                    expectedBody = "test",
                    expectedTimestamp = logTime,
                    expectedContext = null,
                    expectedSeverityNumber = SeverityNumber.FATAL,
                    expectedSeverityText = "DANG",
                    expectedAttributes = mapOf("foo" to "bar")
                )
            },
        )
    }

    private fun Log.assertLogRecord(
        expectedBody: String,
        expectedTimestamp: Long,
        expectedContext: SpanContext?,
        expectedSeverityNumber: SeverityNumber,
        expectedSeverityText: String?,
        expectedAttributes: Map<String, String>,
    ) {
        assertEquals(expectedBody, body)
        assertEquals(expectedTimestamp, timeUnixNano)
        assertEquals(expectedContext?.spanId, spanId)
        assertEquals(expectedContext?.traceId, traceId)
        assertEquals(expectedSeverityNumber.severityNumber, severityNumber)
        assertEquals(expectedSeverityText, severityText)
        with(checkNotNull(attributes)) {
            expectedAttributes.forEach { attr ->
                assertEquals(attr.value, first { attr.key == "foo" }.data)
            }
        }
    }


    private fun EmbracePreSdkStartInterface.setupExporter() {
        embrace.addLogRecordExporter(logExporter)
    }

    private fun EmbraceActionInterface.initializeLogger() {
        embLogger = embrace.getOpenTelemetryKotlin().getLogger("external-logger")
    }
}
