package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbSpan
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.UserAttributes
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.recordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExternalTracerTest {

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
                val span = embTracer.startSpan("test")
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
                    val span = embTracer.startSpan("external-span")
                    startTimeMs = clock.now()
                    val parentContext = embOpenTelemetry.context.root().storeSpan(span)
                    val childSpan = embTracer.startSpan("child-span", parentContext)
                    childSpan.setStatus(StatusData.Error("oh no"))
                    val exception = RuntimeException("bah")
                    childSpan.recordException(exception) {
                        setStringAttribute("bad", "yes")
                    }
                    stacktrace = exception.stackTraceToString()
                    childEndTimeMs = clock.tick()
                    childSpan.end()

                    val embraceSpan = checkNotNull(embrace.startSpan("another-root"))
                    embraceSpan.stop()
                    embTracer.startSpan("no-parent").end()

                    span.setLongAttribute("failures", 1L)
                    span.setStringAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, "spoof")
                    endTimeMs = clock.tick()
                    span.end()
                    embTracer.startSpan("another-parent-with-tracer").end()
                    embTracer.startSpan("set-parent-explicitly", parentContext).end()
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

                // the experiments attribute cannot be set via the OTel API
                assertNull(parent.attributes?.singleOrNull { it.key == EmbCommonAttributes.EMB_EXPERIMENTS })
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
                assertEquals(parent, exportedSpan.toEmbracePayload())
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
                    val parentSpan = embTracer.startSpan("external-span")
                    val parentContext = embOpenTelemetry.context.root().storeSpan(parentSpan)
                    embTracer.startSpan("set-parent-explicitly", parentContext).end()
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
                    val span = embTracer.startSpan("exc-span")
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
    fun `user id on exported span reflects the value at span start`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                recordSession {
                    embTracer.startSpan("no-user-span").end()
                    embrace.setUserIdentifier("user-abc")
                    val ongoingSpan = embTracer.startSpan("ongoing-span")
                    embTracer.startSpan("user-span").end()
                    embrace.setUserIdentifier("user-xyz")
                    embTracer.startSpan("changed-user-span").end()
                    ongoingSpan.end()
                    embrace.setUserIdentifier(null)
                    embTracer.startSpan("cleared-user-span").end()
                }
            },
            assertAction = {
                val spans = spanExporter.exportedSpans.associateBy { it.name }
                assertNull(checkNotNull(spans["no-user-span"]).attributes[UserAttributes.USER_ID])
                assertEquals("user-abc", checkNotNull(spans["user-span"]).attributes[UserAttributes.USER_ID])
                assertEquals("user-abc", checkNotNull(spans["ongoing-span"]).attributes[UserAttributes.USER_ID])
                assertEquals("user-xyz", checkNotNull(spans["changed-user-span"]).attributes[UserAttributes.USER_ID])
                assertNull(checkNotNull(spans["cleared-user-span"]).attributes[UserAttributes.USER_ID])
            }
        )
    }

    @Test
    fun `event and link attributes can be read back from the span`() {
        var span: EmbSpan? = null
        var linkedSpanId: String? = null

        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                recordSession {
                    val linkedSpan = embTracer.startSpan("linked-span")
                    linkedSpanId = linkedSpan.spanContext.spanId
                    val attrSpan = embTracer.startSpan("attr-span") as EmbSpan
                    attrSpan.addEvent("my-event") {
                        setStringAttribute("event-key", "event-value")
                        setLongAttribute("event-count", 3L)
                    }
                    attrSpan.addLink(linkedSpan.spanContext) {
                        setBooleanAttribute("link-flag", true)
                    }
                    span = attrSpan
                    linkedSpan.end()
                    attrSpan.end()
                }
            },
            assertAction = {
                // a stopped span releases its retained event/link data, so read it back from the
                // exported session payload rather than the live span
                checkNotNull(span)
                val sessionMessage = getSingleSessionEnvelope()
                val recorded = checkNotNull(sessionMessage.data.spans?.singleOrNull { it.name == "attr-span" })

                // all attribute values are stringified when written, so they read back as strings
                with(checkNotNull(recorded.events).single { it.name == "my-event" }) {
                    assertEquals(2, attributes?.size)
                    assertEquals("event-value", attributes?.single { it.key == "event-key" }?.data)
                    assertEquals("3", attributes?.single { it.key == "event-count" }?.data)
                }

                with(checkNotNull(recorded.links).single { it.spanId == linkedSpanId }) {
                    assertEquals(1, attributes?.size)
                    assertEquals("true", attributes?.single { it.key == "link-flag" }?.data)
                }
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
                val span = tracer.startSpan("test-span")

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
