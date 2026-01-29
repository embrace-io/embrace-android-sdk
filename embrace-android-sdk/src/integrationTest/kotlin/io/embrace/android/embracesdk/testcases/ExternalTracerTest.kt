package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.getTracer
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.data.SpanData
import io.embrace.opentelemetry.kotlin.tracing.data.StatusData
import io.embrace.opentelemetry.kotlin.tracing.recordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class ExternalTracerTest : RobolectricTest() {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var embOpenTelemetry: OpenTelemetry
    private lateinit var embTracer: Tracer

    private val remoteConfig = RemoteConfig(
        otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100.0f) // Enable Kotlin SDK
    )

    @Before
    fun setup() {
        spanExporter = FakeSpanExporter()
    }

    @Test
    fun `record a span with getTracer`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
            },
            assertAction = {
                val span = embTracer.createSpan("test")
                assertTrue(span.isRecording())
            }
        )
    }

    @Test
    fun `span created with external tracer works correctly`() {
        var startTimeMs: Long? = null
        var endTimeMs: Long? = null
        var childEndTimeMs: Long? = null
        var stacktrace: String? = null

        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                embOpenTelemetry = embrace.getOpenTelemetryKotlin()
                initializeTracer()
                recordSession {
                    val span = embTracer.createSpan("external-span")
                    startTimeMs = clock.now()
                    val parentContext = embOpenTelemetry.contextFactory.storeSpan(embOpenTelemetry.contextFactory.root(), span)
                    val childSpan = embTracer.createSpan("child-span", parentContext)
                    childSpan.status = StatusData.Error("oh no")
                    val exception = RuntimeException("bah")
                    childSpan.recordException(exception) {
                        setStringAttribute("bad", "yes")
                    }
                    stacktrace = exception.stackTraceToString()
                    childEndTimeMs = clock.tick()
                    childSpan.end()

                    val embraceSpan = checkNotNull(embrace.startSpan("another-root"))
                    embraceSpan.stop()
                    embTracer.createSpan("no-parent").end()

                    span.setLongAttribute("failures", 1L)
                    endTimeMs = clock.tick()
                    span.end()
                    embTracer.createSpan("another-parent-with-tracer").end()
                    embTracer.createSpan("set-parent-explicitly", parentContext).end()
                }
            },
            assertAction = {
                val sessionMessage = getSingleSessionEnvelope()
                val spans = checkNotNull(sessionMessage.data.spans)
                val recordedSpans = spans.associateBy { it.name }
                val parent = checkNotNull(recordedSpans["external-span"])
                val child = checkNotNull(recordedSpans["child-span"])
                val embraceSpan = checkNotNull(recordedSpans["another-root"])
                val noParent = checkNotNull(recordedSpans["no-parent"])
                val anotherTracerSpan = checkNotNull(recordedSpans["another-parent-with-tracer"])
                val setParentExplicitly = checkNotNull(recordedSpans["set-parent-explicitly"])
                assertEquals(parent.traceId, child.traceId)
                assertEquals(parent.traceId, setParentExplicitly.traceId)
                assertNotEquals(parent.traceId, embraceSpan.traceId)
                assertNotEquals(parent.traceId, anotherTracerSpan.traceId)
                assertNotEquals(parent.traceId, noParent.traceId)
                assertEmbraceSpanData(
                    span = parent,
                    expectedStartTimeMs = checkNotNull(startTimeMs),
                    expectedEndTimeMs = checkNotNull(endTimeMs),
                    expectedParentId = OtelIds.INVALID_SPAN_ID,
                    expectedCustomAttributes = mapOf("failures" to "1")
                )
                assertEmbraceSpanData(
                    span = child,
                    expectedStartTimeMs = checkNotNull(startTimeMs),
                    expectedEndTimeMs = checkNotNull(childEndTimeMs),
                    expectedParentId = checkNotNull(parent.spanId),
                    expectedStatus = Span.Status.ERROR,
                    expectedErrorCode = ErrorCode.FAILURE,
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "exception",
                            timestampNanos = checkNotNull(startTimeMs?.millisToNanos()),
                            attributes = listOf(
                                Attribute("bad", "yes"),
                                Attribute(
                                    ExceptionAttributes.EXCEPTION_TYPE,
                                    checkNotNull(RuntimeException::class.java.canonicalName)
                                ),
                                Attribute(ExceptionAttributes.EXCEPTION_MESSAGE, "bah"),
                                Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE, stacktrace)
                            )
                        )
                    )
                )

                val exportedSpan: SpanData = spanExporter.exportedSpans.single { it.name == "external-span" }
                assertEquals(parent.toEmbracePayload(), exportedSpan.toEmbraceSpanData())
                with(exportedSpan.instrumentationScopeInfo) {
                    assertEquals("external-tracer", name)
                    assertNull(schemaUrl)
                }
            }
        )
    }

    @Test
    fun `span with explicit parent`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                embOpenTelemetry = embrace.getOpenTelemetryKotlin()
                recordSession {
                    val parentSpan = embTracer.createSpan("external-span")
                    val parentContext = embOpenTelemetry.contextFactory.storeSpan(embOpenTelemetry.contextFactory.root(), parentSpan)
                    embTracer.createSpan("set-parent-explicitly", parentContext).end()
                    parentSpan.end()
                }
            },
            assertAction = {
                val sessionMessage = getSingleSessionEnvelope()
                val spans = checkNotNull(sessionMessage.data.spans)
                val recordedSpans = spans.associateBy { it.name }
                val parent = checkNotNull(recordedSpans["external-span"])
                val setParentExplicitly = checkNotNull(recordedSpans["set-parent-explicitly"])
                assertEquals(parent.traceId, setParentExplicitly.traceId)
            }
        )
    }


    @Test
    fun `span record exception`() {
        var stacktrace: String? = null
        var startTimeMs: Long? = null
        var endTimeMs: Long? = null

        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                recordSession {
                    startTimeMs = clock.now()
                    val span = embTracer.createSpan("exc-span")
                    val exception = RuntimeException("bah")
                    stacktrace = exception.stackTraceToString()
                    span.recordException(exception) {
                        setStringAttribute("bad", "yes")
                    }
                    span.end()
                    endTimeMs = clock.now()
                }
            },
            assertAction = {
                val sessionMessage = getSingleSessionEnvelope()
                val spans = checkNotNull(sessionMessage.data.spans)
                val recordedSpans = spans.associateBy { it.name }
                val span = checkNotNull(recordedSpans["exc-span"])
                assertEmbraceSpanData(
                    span = span,
                    expectedStartTimeMs = checkNotNull(startTimeMs),
                    expectedEndTimeMs = checkNotNull(endTimeMs),
                    expectedParentId = OtelIds.INVALID_SPAN_ID,
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "exception",
                            timestampNanos = checkNotNull(startTimeMs).millisToNanos(),
                            attributes = listOf(
                                Attribute("bad", "yes"),
                                Attribute(
                                    ExceptionAttributes.EXCEPTION_TYPE,
                                    checkNotNull(RuntimeException::class.java.canonicalName)
                                ),
                                Attribute(ExceptionAttributes.EXCEPTION_MESSAGE, "bah"),
                                Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE, stacktrace)
                            )
                        )
                    )
                )
            }
        )
    }


    @Test
    fun `getOpenTelemetryKotlin returns noop before SDK start`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                val otelKotlin = embrace.getOpenTelemetryKotlin()
                val tracer = otelKotlin.getTracer("test-tracer")
                val span = tracer.createSpan("test-span")

                // Noop span should not be recording
                assertFalse(span.isRecording())
                span.end()
            },
            testCaseAction = {},
            assertAction = {}
        )
    }

    private fun EmbracePreSdkStartInterface.setupExporter() {
        embrace.addSpanExporter(spanExporter)
    }

    private fun EmbraceActionInterface.initializeTracer() {
        embTracer = embrace.getOpenTelemetryKotlin().getTracer("external-tracer")
    }
}
