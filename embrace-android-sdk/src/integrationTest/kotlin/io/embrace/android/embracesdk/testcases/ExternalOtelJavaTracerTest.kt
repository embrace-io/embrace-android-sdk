package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import io.embrace.android.embracesdk.otel.java.addJavaSpanExporter
import io.embrace.android.embracesdk.otel.java.getJavaOpenTelemetry
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.opentelemetry.context.Context
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExternalOtelJavaTracerTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(useKotlinSdk = false)
    }

    private lateinit var spanExporter: FakeOtelJavaSpanExporter
    private lateinit var embOpenTelemetry: OtelJavaOpenTelemetry
    private lateinit var embTracer: OtelJavaTracer

    private val remoteConfig = RemoteConfig(
        killSwitchConfig = KillSwitchRemoteConfig(disableOtelKotlinSdk = true)
    )

    @Before
    fun setup() {
        spanExporter = FakeOtelJavaSpanExporter()
    }

    @Test
    fun `check correctness of implementations used by Tracer`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                embOpenTelemetry = embrace.getJavaOpenTelemetry()
            },
            assertAction = {
                val spanBuilder = embTracer.spanBuilder("test")
                val span = spanBuilder.startSpan()
                assertTrue(span.isRecording)
            }
        )
    }

    @Test
    fun `span created with external tracer works correctly`() {
        var startTimeMs: Long? = null
        var endTimeMs: Long? = null
        var childEndTimeMs: Long? = null
        var stacktrace: String? = null
        var wrappedSpan: OtelJavaSpan? = null
        var parentContext: OtelJavaContext?

        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
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
                        childSpan.setStatus(OtelJavaStatusCode.ERROR)
                        val exception = RuntimeException("bah")
                        childSpan.recordException(exception, OtelJavaAttributes.builder().put("bad", "yes").build())
                        stacktrace = exception.stackTraceToString()
                        childEndTimeMs = clock.tick()
                        childSpan.end()

                        val embraceSpan = checkNotNull(embrace.startSpan("another-root"))
                        embraceSpan.stop()
                        embTracer.spanBuilder("no-parent").setNoParent().startSpan().end()
                        parentContext = OtelJavaContext.current()
                    }
                    span.setAttribute("failures", 1)
                    endTimeMs = clock.tick()
                    span.end()
                    embTracer.spanBuilder("another-parent-with-tracer").startSpan().end()
                    embTracer.spanBuilder("set-parent-explicitly").setParent(OtelJavaContext.root().with(span)).startSpan()
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
                    expectedParentId = OtelIds.INVALID_SPAN_ID,
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
                                Attribute(ExceptionAttributes.EXCEPTION_MESSAGE, "bah"),
                                Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE, stacktrace),
                                Attribute(
                                    ExceptionAttributes.EXCEPTION_TYPE,
                                    checkNotNull(RuntimeException::class.java.canonicalName)
                                )
                            )
                        )
                    )
                )

                assertTrue("Timed out waiting for the span to be exported", spanExporter.awaitSpanExport(3))
                val exportedSpan: OtelJavaSpanData = spanExporter.exportedSpans.single { it.name == "external-span" }
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
    fun `span with explicit parent`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
                recordSession {
                    val spanBuilder = embTracer.spanBuilder("external-span")
                    val span = spanBuilder.startSpan()
                    val ctx = span.storeInContext(Context.current())
                    embTracer.spanBuilder("set-parent-explicitly").setParent(ctx).startSpan().end()
                    span.end()
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
                    val span = embTracer.spanBuilder("exc-span").startSpan()
                    val exception = RuntimeException("bah")
                    stacktrace = exception.stackTraceToString()
                    span.recordException(exception, OtelJavaAttributes.builder().put("bad", "yes").build())
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
                    expectedParentId = OtelJavaSpanContext.getInvalid().spanId,
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "exception",
                            timestampNanos = checkNotNull(startTimeMs?.millisToNanos()),
                            attributes = listOf(
                                Attribute("bad", "yes"),
                                Attribute(ExceptionAttributes.EXCEPTION_MESSAGE, "bah"),
                                Attribute(ExceptionAttributes.EXCEPTION_STACKTRACE, stacktrace),
                                Attribute(
                                    ExceptionAttributes.EXCEPTION_TYPE,
                                    checkNotNull(RuntimeException::class.java.canonicalName)
                                )
                            )
                        )
                    )
                )
            }
        )
    }

    @Test
    fun `opentelemetry instance can be used to log spans`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                setupExporter()
            },
            testCaseAction = {
                initializeTracer()
            },
            assertAction = {
                val spanBuilder = embTracer.spanBuilder("test")
                val span = spanBuilder.startSpan()
                assertTrue(span.isRecording)
            }
        )
    }

    @Test
    fun `getJavaOpenTelemetry returns noop before SDK start`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            preSdkStartAction = {
                val otelJava = embrace.getJavaOpenTelemetry()
                val tracer = otelJava.getTracer("test-tracer")
                val span = tracer.spanBuilder("test-span").startSpan()

                // Noop span should not be recording
                assertEquals(false, span.isRecording)
                span.end()
            },
            testCaseAction = {},
            assertAction = {}
        )
    }

    private fun EmbracePreSdkStartInterface.setupExporter() {
        embrace.addJavaSpanExporter(spanExporter)
    }

    private fun EmbraceActionInterface.initializeTracer() {
        embTracer = embrace.getJavaOpenTelemetry().getTracer(
            "external-tracer",
            "1.0.0"
        )
    }
}
