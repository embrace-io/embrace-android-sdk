package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.recordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
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

    private lateinit var otel: OpenTelemetry
    private lateinit var tracer: Tracer
    private lateinit var logger: Logger

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

    @Test
    fun `span attribute export matches golden file using compat implementation`() {
        assertSpanAttributeExport(useKotlinSdk = false)
    }

    @Test
    fun `span attribute export matches golden file using kmp implementation`() {
        assertSpanAttributeExport(useKotlinSdk = true)
    }

    @Test
    fun `span event export matches golden file using compat implementation`() {
        assertSpanEventExport(useKotlinSdk = false)
    }

    @Test
    fun `span event export matches golden file using kmp implementation`() {
        assertSpanEventExport(useKotlinSdk = true)
    }

    @Ignore("Requires fix to compat context implementation")
    @Test
    fun `span relationship export matches golden file using compat implementation`() {
        assertSpanRelationshipExport(useKotlinSdk = false)
    }

    @Test
    fun `span relationship export matches golden file using kmp implementation`() {
        assertSpanRelationshipExport(useKotlinSdk = true)
    }

    @Test
    fun `log record export matches golden file using compat implementation`() {
        assertLogRecordExport(useKotlinSdk = false)
    }

    @Test
    fun `log record export matches golden file using kmp implementation`() {
        assertLogRecordExport(useKotlinSdk = true)
    }

    @Ignore("Requires fix to compat context implementation")
    @Test
    fun `log span context export matches golden file using compat implementation`() {
        assertLogSpanContextExport(useKotlinSdk = false)
    }

    @Test
    fun `log span context export matches golden file using kmp implementation`() {
        assertLogSpanContextExport(useKotlinSdk = true)
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
     * Exercises every attribute setter on the OTel tracing API
     */
    private fun assertSpanAttributeExport(useKotlinSdk: Boolean) {
        assertOtelApiParity(
            useKotlinSdk = useKotlinSdk,
            testCaseAction = {
                recordSession {
                    val span = tracer.startSpan(
                        name = "original-name",
                        spanKind = SpanKind.CLIENT,
                        startTimestamp = clock.now().millisToNanos(),
                    ) {
                        setStringAttribute("created-with", "attribute")
                    }
                    assertTrue(tracer.enabled())
                    assertTrue(span.isRecording())
                    assertTrue(span.spanContext.isValid)

                    with(span) {
                        setStringAttribute("string-attr", "value")
                        setLongAttribute("long-attr", 42L)
                        setDoubleAttribute("double-attr", 1.5)
                        setBooleanAttribute("boolean-attr", true)
                        setStringListAttribute("string-list-attr", listOf("a", "b"))
                        setLongListAttribute("long-list-attr", listOf(1L, 2L))
                        setDoubleListAttribute("double-list-attr", listOf(1.5, 2.5))
                        setBooleanListAttribute("boolean-list-attr", listOf(true, false))
                        setByteArrayAttribute("byte-array-attr", byteArrayOf(1, 2, 3))
                        setAnyValueAttribute("any-value-attr", AnyValue.StringValue("wrapped"))
                        setName("renamed-span")
                        setStatus(StatusData.Ok)
                    }
                    span.end(timestamp = clock.tick(1000L).millisToNanos())
                    assertFalse(span.isRecording())
                }
            },
            otelExportAssertion = {
                assertSpansMatchParityGoldenFile(
                    spans = awaitApiSpans(1),
                    goldenFile = "otel-export-parity-span-attributes.json",
                )
            },
        )
    }

    /**
     * Exercises the span event API
     */
    private fun assertSpanEventExport(useKotlinSdk: Boolean) {
        assertOtelApiParity(
            useKotlinSdk = useKotlinSdk,
            testCaseAction = {
                recordSession {
                    val span = tracer.startSpan("event-span")
                    span.addEvent("plain-event")
                    span.addEvent(
                        name = "detailed-event",
                        timestamp = clock.tick(500L).millisToNanos(),
                    ) {
                        setStringAttribute("event-string", "value")
                        setLongAttribute("event-long", 7L)
                        setBooleanAttribute("event-boolean", false)
                    }
                    span.recordException(IllegalStateException("something broke")) {
                        setStringAttribute("handled", "true")
                    }
                    span.setStatus(StatusData.Error("it went wrong"))
                    span.end()
                }
            },
            otelExportAssertion = {
                assertSpansMatchParityGoldenFile(
                    spans = awaitApiSpans(1),
                    goldenFile = "otel-export-parity-span-events.json",
                )
            },
        )
    }

    /**
     * Exercises the ways in which one span can reference another: an explicit parent context, and links
     * added both at creation time and afterwards.
     */
    private fun assertSpanRelationshipExport(useKotlinSdk: Boolean) {
        assertOtelApiParity(
            useKotlinSdk = useKotlinSdk,
            testCaseAction = {
                recordSession {
                    val parent = tracer.startSpan("aaa-parent-span")
                    val parentContext = otel.context.root().storeSpan(parent)
                    val child = tracer.startSpan("bbb-child-span", parentContext = parentContext)
                    val linking = tracer.startSpan("ccc-linking-span") {
                        addLink(parent.spanContext) {
                            setBooleanAttribute("link-flag", true)
                        }
                    }
                    linking.addLink(child.spanContext)

                    child.end()
                    linking.end()
                    parent.end()
                }
            },
            otelExportAssertion = {
                val spans = awaitApiSpans(3)
                assertSpansMatchParityGoldenFile(
                    spans = spans,
                    goldenFile = "otel-export-parity-span-relationships.json",
                )

                // the golden file references spans by name, so assert the IDs it resolves them from
                val parent = spans.single { it.name == "aaa-parent-span" }
                val child = spans.single { it.name == "bbb-child-span" }
                val linking = spans.single { it.name == "ccc-linking-span" }
                assertEquals(parent.spanId, child.parentSpanId)
                assertEquals(parent.traceId, child.traceId)
                assertNotEquals(parent.traceId, linking.traceId)
            },
        )
    }

    /**
     * Exercises the fields of a log record emitted via the OTel logging API, across several severities.
     */
    private fun assertLogRecordExport(useKotlinSdk: Boolean) {
        assertOtelApiParity(
            useKotlinSdk = useKotlinSdk,
            testCaseAction = {
                recordSession {
                    val observedTimestamp = clock.now().millisToNanos()
                    logger.emit(
                        body = "log-a",
                        timestamp = clock.tick(100L).millisToNanos(),
                        observedTimestamp = observedTimestamp,
                        severityNumber = SeverityNumber.TRACE,
                        severityText = "TRACE",
                    ) {
                        setStringAttribute("string-attr", "value")
                        setLongAttribute("long-attr", 42L)
                    }
                    assertTrue(logger.enabled())

                    logger.emit(
                        body = "log-b",
                        timestamp = clock.tick(100L).millisToNanos(),
                        observedTimestamp = observedTimestamp,
                        severityNumber = SeverityNumber.INFO,
                        severityText = "INFO",
                    ) {
                        setDoubleAttribute("double-attr", 1.5)
                        setBooleanListAttribute("boolean-list-attr", listOf(true, false))
                        setAnyValueAttribute("any-value-attr", AnyValue.LongValue(3L))
                    }

                    logger.emit(
                        body = 42L,
                        timestamp = clock.tick(100L).millisToNanos(),
                        observedTimestamp = observedTimestamp,
                        severityNumber = SeverityNumber.FATAL,
                        severityText = "FATAL",
                    )
                }
            },
            otelExportAssertion = {
                assertLogsMatchParityGoldenFile(
                    logs = awaitApiLogs(3),
                    goldenFile = "otel-export-parity-log-record.json",
                )
            },
        )
    }

    /**
     * Exercises correlating a log record with a span by emitting it with a context that holds one.
     */
    private fun assertLogSpanContextExport(useKotlinSdk: Boolean) {
        var spanContext: SpanContext? = null

        assertOtelApiParity(
            useKotlinSdk = useKotlinSdk,
            testCaseAction = {
                recordSession {
                    val span = tracer.startSpan("log-parent-span")
                    spanContext = span.spanContext
                    val timestamp = clock.now().millisToNanos()

                    logger.emit(
                        body = "in-span",
                        timestamp = timestamp,
                        observedTimestamp = timestamp,
                        context = otel.context.root().storeSpan(span),
                        severityNumber = SeverityNumber.WARN,
                        severityText = "WARN",
                    ) {
                        setStringAttribute("string-attr", "value")
                    }
                    logger.emit(
                        body = "no-span",
                        timestamp = timestamp,
                        observedTimestamp = timestamp,
                        severityNumber = SeverityNumber.WARN,
                        severityText = "WARN",
                    )
                    span.end()
                }
            },
            otelExportAssertion = {
                val logs = awaitApiLogs(2)
                assertLogsMatchParityGoldenFile(
                    logs = logs,
                    goldenFile = "otel-export-parity-log-span-context.json",
                )

                // the golden file representation omits the span context, as its IDs are random
                val inSpan = logs.single { it.bodyValue?.asString() == "in-span" }
                val noSpan = logs.single { it.bodyValue?.asString() == "no-span" }
                with(checkNotNull(spanContext)) {
                    assertEquals(spanId, inSpan.spanContext.spanId)
                    assertEquals(traceId, inSpan.spanContext.traceId)
                }
                assertFalse(noSpan.spanContext.isValid)
            },
        )
    }

    /**
     * Runs a test case that drives telemetry through the OTel API surface obtained from the SDK.
     */
    private fun assertOtelApiParity(
        useKotlinSdk: Boolean,
        testCaseAction: EmbraceActionInterface.() -> Unit,
        otelExportAssertion: EmbraceOtelExportAssertionInterface.() -> Unit,
    ) {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig(useKotlinSdk),
            testCaseAction = {
                otel = embrace.getOpenTelemetryKotlin()
                tracer = otel.tracerProvider.getTracer(name = TRACER_NAME, version = "1.0.0")
                logger = otel.loggerProvider.getLogger(name = LOGGER_NAME, version = "1.1.0")
                testCaseAction()
            },
            otelExportAssertion = otelExportAssertion,
        )
    }

    /**
     * Awaits the spans recorded via the OTel API, ignoring those the SDK records itself. Spans are sorted
     * by name as the golden file comparison is order-sensitive.
     */
    private fun EmbraceOtelExportAssertionInterface.awaitApiSpans(expectedCount: Int) =
        awaitSpans(expectedCount) { it.instrumentationScopeInfo.name == TRACER_NAME }.sortedBy { it.name }

    /**
     * Awaits the logs recorded via the OTel API, ignoring those the SDK records itself. Logs are sorted
     * by body as the golden file comparison is order-sensitive.
     */
    private fun EmbraceOtelExportAssertionInterface.awaitApiLogs(expectedCount: Int) =
        awaitLogs(expectedCount) { it.instrumentationScopeInfo.name == LOGGER_NAME }
            .sortedBy { it.bodyValue?.asString() }

    /**
     * Selects the implementation under test.
     */
    private fun instrumentedConfig(useKotlinSdk: Boolean) = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(otelKotlinSdkEnabled = useKotlinSdk)
    )

    private companion object {
        private const val SPAN_NAME = "test-span"
        private const val LOG_MESSAGE = "test message"
        private const val TRACER_NAME = "external-tracer"
        private const val LOGGER_NAME = "external-logger"
    }
}
