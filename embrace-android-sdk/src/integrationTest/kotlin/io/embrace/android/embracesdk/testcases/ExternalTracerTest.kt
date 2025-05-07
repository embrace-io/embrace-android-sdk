package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.impl.EmbSpan
import io.embrace.android.embracesdk.internal.otel.impl.EmbSpanBuilder
import io.embrace.android.embracesdk.internal.otel.impl.EmbTracer
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    private lateinit var otelTracer: Tracer

    @Before
    fun setup() {
        spanExporter = FakeSpanExporter()
    }

    @Test
    fun `check correctness of implementations used by Tracer`() {
        testRule.runTest(
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                embOpenTelemetry = embrace.getOpenTelemetry()
                otelTracer = embrace.getOpenTelemetry().getTracer("foo")
            },
            assertAction = {
                assertSame(
                    otelTracer,
                    embOpenTelemetry.getTracer("foo")
                )
                assertTrue(embTracer is EmbTracer)
                val spanBuilder = embTracer.spanBuilder("test")
                val span = spanBuilder.startSpan()
                assertTrue(spanBuilder is EmbSpanBuilder)
                assertTrue(span is EmbSpan)
            }
        )
    }

    @Test
    fun `span created with external tracer works correctly`() {
        var startTimeMs: Long? = null
        var endTimeMs: Long? = null
        var childEndTimeMs: Long? = null
        var stacktrace: String? = null
        var wrappedSpan: Span? = null
        var parentContext: Context?

        testRule.runTest(
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                recordSession {
                    val spanBuilder = embTracer.spanBuilder("external-span")
                    startTimeMs = clock.now()
                    val span = spanBuilder.startSpan()
                    span.makeCurrent().use {
                        val childSpan = embTracer.spanBuilder("child-span").startSpan()
                        childSpan.setStatus(StatusCode.ERROR)
                        val exception = RuntimeException("bah")
                        childSpan.recordException(exception, Attributes.builder().put("bad", "yes").build())
                        stacktrace = exception.truncatedStacktraceText()
                        childEndTimeMs = clock.tick()
                        childSpan.end()

                        val embraceSpan = checkNotNull(embrace.startSpan("another-root"))
                        embraceSpan.stop()
                        embTracer.spanBuilder("no-parent").setNoParent().startSpan().end()
                        parentContext = Context.current()
                    }
                    span.setAttribute("failures", 1)
                    endTimeMs = clock.tick()
                    span.end()
                    embTracer.spanBuilder("another-parent-with-tracer").startSpan().end()
                    embTracer.spanBuilder("set-parent-explicitly").setParent(Context.root().with(span)).startSpan()
                        .end()
                    checkNotNull(parentContext).wrap(Runnable {
                        wrappedSpan = embTracer.spanBuilder("wrapped").startSpan()
                    }).run()
                    checkNotNull(wrappedSpan).end()
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
                val wrapped = checkNotNull(recordedSpans["wrapped"])
                assertEquals(parent.traceId, child.traceId)
                assertEquals(parent.traceId, setParentExplicitly.traceId)
                assertEquals(parent.traceId, wrapped.traceId)
                assertNotEquals(parent.traceId, embraceSpan.traceId)
                assertNotEquals(parent.traceId, anotherTracerSpan.traceId)
                assertNotEquals(parent.traceId, noParent.traceId)
                assertEmbraceSpanData(
                    span = parent,
                    expectedStartTimeMs = checkNotNull(startTimeMs),
                    expectedEndTimeMs = checkNotNull(endTimeMs),
                    expectedParentId = OtelIds.invalidSpanId,
                    expectedCustomAttributes = mapOf("failures" to "1")
                )
                assertEmbraceSpanData(
                    span = child,
                    expectedStartTimeMs = checkNotNull(startTimeMs),
                    expectedEndTimeMs = checkNotNull(childEndTimeMs),
                    expectedParentId = checkNotNull(parent.spanId),
                    expectedStatus = io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR,
                    expectedErrorCode = ErrorCode.FAILURE,
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "exception",
                            timestampNanos = checkNotNull(startTimeMs?.millisToNanos()),
                            attributes = listOf(
                                Attribute("bad", "yes"),
                                Attribute(ExceptionAttributes.EXCEPTION_MESSAGE.key, "bah"),
                                Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE.key, stacktrace),
                                Attribute(
                                    ExceptionAttributes.EXCEPTION_TYPE.key,
                                    checkNotNull(RuntimeException::class.java.canonicalName)
                                )
                            )
                        )
                    )
                )

                assertTrue("Timed out waiting for the span to be exported", spanExporter.awaitSpanExport(3))
                val exportedSpan: SpanData = spanExporter.exportedSpans.single { it.name == "external-span" }
                assertEquals(parent.toEmbracePayload(), exportedSpan.toEmbraceSpanData())
                with(exportedSpan.instrumentationScopeInfo) {
                    assertEquals("external-tracer", name)
                    assertEquals("1.0.0", version)
                    assertNull(schemaUrl)
                }
            }
        )
    }

    @Test
    fun `opentelemetry instance can be used to log spans`() {
        testRule.runTest(
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
            },
            assertAction = {
                assertTrue(embTracer is EmbTracer)
                val spanBuilder = embTracer.spanBuilder("test")
                val span = spanBuilder.startSpan()
                assertTrue(spanBuilder is EmbSpanBuilder)
                assertTrue(span is EmbSpan)
            }
        )
    }

    private fun EmbracePreSdkStartInterface.setupExporter() {
        embrace.addSpanExporter(spanExporter)
    }

    private fun EmbraceActionInterface.initializeTracer() {
        embTracer = embrace.getOpenTelemetry().getTracer(
            "external-tracer",
            "1.0.0"
        )
    }
}
