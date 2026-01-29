package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.getTracer
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.embrace.opentelemetry.kotlin.semconv.ServiceAttributes
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalApi::class, IncubatingApi::class)
@RunWith(AndroidJUnit4::class)
internal class ExternalLoggerTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val instrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            bgActivityCapture = true,
            stateCaptureEnabled = true
        ),
        project = FakeProjectConfig(
            appId = "abcde",
            packageName = "my-cool-app"
        )
    )

    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var embOpenTelemetry: OpenTelemetry
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
        var exportedOTelLog: ReadableLogRecord? = null
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
                embrace.setResourceAttribute("my-resource-attr", "foo")
            },
            testCaseAction = {
                initializeOTel()
                observedTime = clock.now().millisToNanos()
                clock.tick()
                recordSession {
                    logTime = clock.now().millisToNanos()
                    embrace.addSessionProperty("session-attr", "blah", true)
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
                val sessionId = getSingleSessionEnvelope().getSessionId()
                exportedOTelLog = logExporter.exportedLogs.single()
                with(exportedOTelLog) {
                    assertOTelLogRecord(
                        expectedInstrumentationName = "external-logger",
                        expectedInstrumentationVersion = "1.1.0",
                        expectedResourceAttributes = mapOf(
                            ServiceAttributes.SERVICE_NAME to "my-cool-app",
                            "my-resource-attr" to "foo"),
                        expectedEventName = null,
                        expectedBody = "test",
                        expectedObservedTimestamp = observedTime,
                        expectedTimestamp = logTime,
                        expectedSpanContext = embOpenTelemetry.spanContextFactory.invalid,
                        expectedSeverityNumber = SeverityNumber.FATAL,
                        expectedSeverityText = "DANG",
                        expectedSessionId = sessionId,
                        expectedAppState = AppState.FOREGROUND,
                        expectedSessionProperties = mapOf("session-attr" to "blah"),
                        expectedAttributes = mapOf("foo" to "bar"),
                    )
                }
                assertEquals(exportedOTelLog.toEmbracePayload(), getSingleLogEnvelope().getLastLog())
            },
            otelExportAssertion = {
                assertJavaOTelLogRecord(checkNotNull(exportedOTelLog))
            }
        )
    }

    @Test
    fun `record an event with otel logging API in the background with a span parent`() {
        var logTime: Long = -1L
        var observedTime: Long = -1L
        var sessionId = ""
        var parentContext: SpanContext? = null
        var exportedOTelLog: ReadableLogRecord? = null
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
                embrace.setResourceAttribute("my-resource-attr", "foo")
            },
            testCaseAction = {
                initializeOTel()
                observedTime = clock.now().millisToNanos()
                clock.tick()
                logTime = clock.now().millisToNanos()
                embrace.addSessionProperty("bg-attr", "blah", true)
                sessionId = checkNotNull(embrace.currentSessionId)
                val span = embOpenTelemetry.getTracer("").createSpan("my-span")
                parentContext = span.spanContext
                embLogger.logEvent(
                    eventName = "my.event",
                    body = "event",
                    timestamp = logTime,
                    observedTimestamp = observedTime,
                    context = embOpenTelemetry.contextFactory.storeSpan(embOpenTelemetry.contextFactory.root(), span),
                    severityNumber = SeverityNumber.INFO,
                    severityText = "",
                ) {
                    setStringAttribute("foo", "bar")
                }
                clock.tick(2000L)
            },
            assertAction = {
                exportedOTelLog = logExporter.exportedLogs.single()
                with(exportedOTelLog) {
                    assertOTelLogRecord(
                        expectedInstrumentationName = "external-logger",
                        expectedInstrumentationVersion = "1.1.0",
                        expectedResourceAttributes = mapOf(
                            ServiceAttributes.SERVICE_NAME to "my-cool-app",
                            "my-resource-attr" to "foo"),
                        expectedEventName = "my.event",
                        expectedBody = "event",
                        expectedObservedTimestamp = observedTime,
                        expectedTimestamp = logTime,
                        expectedSpanContext = checkNotNull(parentContext),
                        expectedSeverityNumber = SeverityNumber.INFO,
                        expectedSeverityText = "",
                        expectedSessionId = sessionId,
                        expectedAppState = AppState.BACKGROUND,
                        expectedSessionProperties = mapOf("bg-attr" to "blah"),
                        expectedAttributes = mapOf("foo" to "bar"),
                    )
                }
                assertEquals(exportedOTelLog.toEmbracePayload(), getSingleLogEnvelope().getLastLog())
            },
            otelExportAssertion = {
                assertJavaOTelLogRecord(checkNotNull(exportedOTelLog))
            }
        )
    }

    private fun EmbracePreSdkStartInterface.setupExporter() {
        embrace.addLogRecordExporter(logExporter)
    }

    private fun EmbraceActionInterface.initializeOTel() {
        embOpenTelemetry = embrace.getOpenTelemetryKotlin()
        embLogger = embOpenTelemetry.loggerProvider.getLogger(name = "external-logger", version = "1.1.0")
    }

    private fun ReadableLogRecord.assertOTelLogRecord(
        expectedInstrumentationName: String,
        expectedInstrumentationVersion: String,
        expectedResourceAttributes: Map<String, String>,
        expectedEventName: String?,
        expectedBody: String,
        expectedObservedTimestamp: Long,
        expectedTimestamp: Long,
        expectedSpanContext: SpanContext,
        expectedSeverityNumber: SeverityNumber,
        expectedSeverityText: String?,
        expectedSessionId: String?,
        expectedAppState: AppState,
        expectedSessionProperties: Map<String, String>,
        expectedAttributes: Map<String, String>,
    ) {
        assertEquals(expectedInstrumentationName, instrumentationScopeInfo.name)
        assertEquals(expectedInstrumentationVersion, instrumentationScopeInfo.version)
        val resourceAttributes = resource.attributes.mapValues { it.value.toString() }
        assertNotNull(resourceAttributes[ServiceAttributes.SERVICE_NAME])
        with(resourceAttributes) {
            expectedResourceAttributes.forEach { attr ->
                assertEquals(attr.value, this[attr.key])
            }
        }

        if (expectedEventName != null) {
            assertEquals(expectedEventName, eventName)
        } else {
            assertNull(eventName)
        }
        assertEquals(expectedBody, body)
        assertEquals(expectedObservedTimestamp, observedTimestamp)
        assertEquals(expectedTimestamp, timestamp)
        assertEquals(expectedSpanContext, spanContext)
        assertEquals(expectedSeverityNumber.severityNumber, severityNumber?.severityNumber)
        assertEquals(expectedSeverityText, severityText)
        with(checkNotNull(attributes.mapValues { it.value.toString() })) {
            assertNotNull(filter { it.key == LogAttributes.LOG_RECORD_UID }.size)
            if (expectedSessionId != null) {
                assertEquals(expectedSessionId, this[SessionAttributes.SESSION_ID])
            } else {
                assertFalse(containsKey(SessionAttributes.SESSION_ID))
            }
            assertEquals(expectedAppState.description, this[embState.name])
            assertTrue(containsKey("emb.state.test"))
            expectedSessionProperties.forEach { prop ->
                assertEquals(prop.value, this[prop.key.toEmbraceAttributeName()])
            }
            expectedAttributes.forEach { attr ->
                assertEquals(attr.value, this[attr.key])
            }
        }
    }

    private fun EmbraceOtelExportAssertionInterface.assertJavaOTelLogRecord(expectedOTelLog: ReadableLogRecord) {
        val logId = expectedOTelLog.attributes[LogAttributes.LOG_RECORD_UID].toString()
        val logRecord = awaitLogs(1) { it.attributes.toStringMap()[LogAttributes.LOG_RECORD_UID] == logId }.single()
        with(logRecord) {
            assertEquals(expectedOTelLog.resource.attributes, resource.attributes.toStringMap())
            assertEquals(expectedOTelLog.instrumentationScopeInfo.name, instrumentationScopeInfo.name)
            assertEquals(expectedOTelLog.body, bodyValue?.value)
            assertEquals(expectedOTelLog.observedTimestamp, observedTimestampEpochNanos)
            assertEquals(expectedOTelLog.timestamp, timestampEpochNanos)
            assertEquals(expectedOTelLog.spanContext.spanId, spanContext.spanId)
            assertEquals(expectedOTelLog.spanContext.traceId, spanContext.traceId)
            assertEquals(expectedOTelLog.severityNumber?.severityNumber, severity.severityNumber)
            assertEquals(expectedOTelLog.severityText, severityText)
            attributes.toStringMap().forEach { attr ->
                assertEquals(expectedOTelLog.attributes[attr.key].toString(), attr.value)
            }
        }
    }
}
