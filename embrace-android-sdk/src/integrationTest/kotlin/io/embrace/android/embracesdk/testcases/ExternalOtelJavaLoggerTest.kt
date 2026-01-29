package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.otel.java.addJavaLogRecordExporter
import io.embrace.android.embracesdk.otel.java.getJavaOpenTelemetry
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogger
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSeverity
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.embrace.opentelemetry.kotlin.semconv.ServiceAttributes
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class, IncubatingApi::class)
@RunWith(AndroidJUnit4::class)
internal class ExternalOtelJavaLoggerTest {

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

    private lateinit var logExporter: FakeOtelJavaLogRecordExporter
    private lateinit var embOpenTelemetry: OtelJavaOpenTelemetry
    private lateinit var otelLogger: OtelJavaLogger

    private val remoteConfig = RemoteConfig(
        otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0.0f)
    )

    @Before
    fun setup() {
        logExporter = FakeOtelJavaLogRecordExporter()
    }

    @Test
    fun `record a log with java otel logging API during a session`() {
        var logTime: Long = -1L
        var observedTime: Long = -1L
        var exportedOTelLog: OtelJavaLogRecordData?
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
                    otelLogger
                        .logRecordBuilder()
                        .setBody("test")
                        .setTimestamp(logTime, TimeUnit.NANOSECONDS)
                        .setObservedTimestamp(observedTime, TimeUnit.NANOSECONDS)
                        .setSeverity(OtelJavaSeverity.FATAL)
                        .setSeverityText("DANG")
                        .setAttribute("foo", "bar")
                        .emit()
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
                        expectedSpanContext = OtelJavaSpanContext.getInvalid(),
                        expectedSeverity = OtelJavaSeverity.FATAL,
                        expectedSeverityText = "DANG",
                        expectedSessionId = sessionId,
                        expectedAppState = AppState.FOREGROUND,
                        expectedSessionProperties = mapOf("session-attr" to "blah"),
                        expectedAttributes = mapOf("foo" to "bar"),
                    )
                }
                getSingleLogEnvelope().getLastLog().assertExpected(exportedOTelLog)
            }
        )
    }

    @Test
    fun `record an event with java otel logging API in the background with a span parent`() {
        var logTime: Long = -1L
        var observedTime: Long = -1L
        var sessionId = ""
        var parentContext: OtelJavaSpanContext? = null
        var exportedOTelLog: OtelJavaLogRecordData?
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
                val span = embOpenTelemetry.getTracer("").spanBuilder("my-span").startSpan()
                val logContext = span.storeInContext(OtelJavaContext.root())
                parentContext = span.spanContext
                otelLogger
                    .logRecordBuilder()
                    .setEventName("my.event")
                    .setBody("event")
                    .setTimestamp(logTime, TimeUnit.NANOSECONDS)
                    .setObservedTimestamp(observedTime, TimeUnit.NANOSECONDS)
                    .setSeverity(OtelJavaSeverity.INFO)
                    .setContext(logContext)
                    .setAttribute("foo", "bar")
                    .emit()
                span.end()
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
                        expectedSeverity = OtelJavaSeverity.INFO,
                        expectedSeverityText = null,
                        expectedSessionId = sessionId,
                        expectedAppState = AppState.BACKGROUND,
                        expectedSessionProperties = mapOf("bg-attr" to "blah"),
                        expectedAttributes = mapOf("foo" to "bar"),
                    )
                }
                getSingleLogEnvelope().getLastLog().assertExpected(exportedOTelLog)
            },
        )
    }

    private fun EmbracePreSdkStartInterface.setupExporter() {
        embrace.addJavaLogRecordExporter(logExporter)
    }

    private fun EmbraceActionInterface.initializeOTel() {
        embOpenTelemetry = embrace.getJavaOpenTelemetry()
        otelLogger = embOpenTelemetry
            .logsBridge
            .loggerBuilder("external-logger")
            .setInstrumentationVersion("1.1.0")
            .build()
    }

    private fun OtelJavaLogRecordData.assertOTelLogRecord(
        expectedInstrumentationName: String,
        expectedInstrumentationVersion: String,
        expectedResourceAttributes: Map<String, String>,
        expectedEventName: String?,
        expectedBody: String,
        expectedObservedTimestamp: Long,
        expectedTimestamp: Long,
        expectedSpanContext: OtelJavaSpanContext,
        expectedSeverity: OtelJavaSeverity,
        expectedSeverityText: String?,
        expectedSessionId: String?,
        expectedAppState: AppState,
        expectedSessionProperties: Map<String, String>,
        expectedAttributes: Map<String, String>,
    ) {
        assertEquals(expectedInstrumentationName, instrumentationScopeInfo.name)
        assertEquals(expectedInstrumentationVersion, instrumentationScopeInfo.version)
        val resourceAttributes = resource.attributes.toStringMap()
        assertNotNull(resourceAttributes[ServiceAttributes.SERVICE_NAME])
        with(resourceAttributes) {
            expectedResourceAttributes.forEach { attr ->
                assertEquals(attr.value, this[attr.key])
            }
        }

        // OtelJavaLogRecordBuilderAdapter has a bug and isn't passing through the event name - uncomment when fixed

//        if (expectedEventName != null) {
//            assertEquals(expectedEventName, eventName)
//        } else {
//            assertNull(eventName)
//        }

        assertEquals(expectedBody, bodyValue?.value)
        assertEquals(expectedObservedTimestamp, observedTimestampEpochNanos)
        assertEquals(expectedTimestamp, timestampEpochNanos)
        assertEquals(expectedSpanContext, spanContext)
        assertEquals(expectedSeverity.severityNumber, severity?.severityNumber)
        assertEquals(expectedSeverityText, severityText)
        with(checkNotNull(attributes.toStringMap())) {
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

    private fun Log.assertExpected(expectedOtelJavaLogRecordData: OtelJavaLogRecordData) {
        assertEquals(expectedOtelJavaLogRecordData.bodyValue?.value, body)
        assertEquals(expectedOtelJavaLogRecordData.timestampEpochNanos, timeUnixNano)
        assertEquals(expectedOtelJavaLogRecordData.spanContext.spanId, spanId ?: OtelJavaSpanContext.getInvalid().spanId)
        assertEquals(expectedOtelJavaLogRecordData.spanContext.traceId, traceId ?: OtelJavaSpanContext.getInvalid().traceId)
        assertEquals(expectedOtelJavaLogRecordData.severity?.severityNumber, severityNumber)
        assertEquals(expectedOtelJavaLogRecordData.severityText, severityText)
        val expectedAttributes = expectedOtelJavaLogRecordData.attributes.toStringMap()
        checkNotNull(attributes).forEach { attr ->
            assertEquals(expectedAttributes[attr.key], attr.data)
        }
    }
}
