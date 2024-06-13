package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.opentelemetry.EmbSpan
import io.embrace.android.embracesdk.opentelemetry.EmbSpanBuilder
import io.embrace.android.embracesdk.opentelemetry.EmbTracer
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class ExternalTracerTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var embTracer: Tracer
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        spanExporter = FakeSpanExporter()
        with(testRule) {
            clock = harness.overriddenClock
            embrace.addSpanExporter(spanExporter)
            startSdk(context = harness.overriddenCoreModule.context)
            embTracer = checkNotNull(embrace.getTracer("external-tracer", "1.0.0"))
        }
    }

    @Test
    fun `check correctness of implementations used by Tracer`() {
        assertTrue(embTracer is EmbTracer)
        val spanBuilder = embTracer.spanBuilder("test")
        val span = spanBuilder.startSpan()
        assertTrue(spanBuilder is EmbSpanBuilder)
        assertTrue(span is EmbSpan)
    }

    @Test
    fun `span created with external tracer works correctly`() {
        with(testRule) {
            var startTimeMs: Long? = null
            var endTimeMs: Long? = null
            var childEndTimeMs: Long? = null
            var stacktrace: String? = null
            var wrappedSpan: Span? = null
            var parentContext: Context?
            val sessionMessage = harness.recordSession {
                val spanBuilder = embTracer.spanBuilder("external-span")
                startTimeMs = harness.overriddenClock.now()
                val span = spanBuilder.startSpan()
                span.makeCurrent().use {
                    val childSpan = embTracer.spanBuilder("child-span").startSpan()
                    childSpan.setStatus(StatusCode.ERROR)
                    val exception = RuntimeException("bah")
                    childSpan.recordException(exception, Attributes.builder().put("bad", "yes").build())
                    stacktrace = exception.truncatedStacktraceText()
                    childEndTimeMs = harness.overriddenClock.tick()
                    childSpan.end()

                    val embraceSpan = checkNotNull(embrace.startSpan("another-root"))
                    embraceSpan.stop()
                    embTracer.spanBuilder("no-parent").setNoParent().startSpan().end()
                    parentContext = Context.current()
                }
                span.setAttribute("failures", 1)
                endTimeMs = harness.overriddenClock.tick()
                span.end()
                embTracer.spanBuilder("another-parent-with-tracer").startSpan().end()
                embTracer.spanBuilder("set-parent-explicitly").setParent(Context.root().with(span)).startSpan().end()
                checkNotNull(parentContext).wrap(Runnable { wrappedSpan = embTracer.spanBuilder("wrapped").startSpan() }).run()
                checkNotNull(wrappedSpan).end()
            }
            val spans = checkNotNull(sessionMessage?.spans)
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
                expectedParentId = SpanId.getInvalid(),
                expectedCustomAttributes = mapOf("failures" to "1"),
                key = true
            )
            assertEmbraceSpanData(
                span = child,
                expectedStartTimeMs = checkNotNull(startTimeMs),
                expectedEndTimeMs = checkNotNull(childEndTimeMs),
                expectedParentId = parent.spanId,
                expectedStatus = StatusCode.ERROR,
                expectedErrorCode = ErrorCode.FAILURE,
                expectedEvents = listOf(
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "exception",
                            timestampMs = checkNotNull(startTimeMs),
                            attributes = mapOf(
                                exceptionType.key to checkNotNull(RuntimeException::class.java.canonicalName),
                                exceptionMessage.key to "bah",
                                exceptionStacktrace.key to checkNotNull(stacktrace),
                                "bad" to "yes",
                            )
                        )
                    )
                ),
                key = false
            )

            assertTrue("Timed out waiting for the span to be exported", spanExporter.awaitSpanExport(3))
            val exportedSpan = spanExporter.exportedSpans.single { it.name == "external-span" }
            assertEquals(parent, EmbraceSpanData(exportedSpan))
            with(exportedSpan.instrumentationScopeInfo) {
                assertEquals("external-tracer", name)
                assertEquals("1.0.0", version)
                assertNull(schemaUrl)
            }
        }
    }
}