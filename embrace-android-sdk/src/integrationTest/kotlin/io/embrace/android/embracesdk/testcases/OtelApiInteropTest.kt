package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.otel.java.getJavaOpenTelemetry
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.opentelemetry.kotlin.aliases.OtelJavaAttributes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Sanity checks that each API surface exports spans and logs on both the Java and Kotlin SDK implementations.
 *
 * A span and log are created using the Embrace, opentelemetry-java, and opentelemetry-kotlin APIs. Each test
 * runs once per [OtelSdkMode], which controls whether the 'compat' or 'regular' mode of opentelemetry-kotlin is used.
 *
 * Assertions verify the exported OTLP is the same, barring minor differences.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class OtelApiInteropTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `java api`() {
        assertTelemetryExported { recordJavaApiTelemetry() }
    }

    @Test
    fun `kotlin api`() {
        assertTelemetryExported { recordKotlinApiTelemetry() }
    }

    @Test
    fun `embrace api`() {
        assertTelemetryExported { recordEmbraceApiTelemetry() }
    }

    private fun EmbraceActionInterface.recordJavaApiTelemetry() {
        val otel = embrace.getJavaOpenTelemetry()
        val span = otel.getTracer(TRACER_NAME).spanBuilder(SPAN).setAttribute(ATTR_KEY, ATTR_VALUE).startSpan()
        span.addEvent(EVENT, OtelJavaAttributes.builder().put(ATTR_KEY, ATTR_VALUE).build())
        span.makeCurrent().use {
            otel.logsBridge.get(LOGGER_NAME).logRecordBuilder()
                .setBody(LOG)
                .setAttribute(ATTR_KEY, ATTR_VALUE)
                .emit()
        }
        span.end()
    }

    private fun EmbraceActionInterface.recordKotlinApiTelemetry() {
        val otel = embrace.getOpenTelemetryKotlin()
        val span = otel.tracerProvider.getTracer(TRACER_NAME).startSpan(SPAN) {
            setStringAttribute(ATTR_KEY, ATTR_VALUE)
        }
        span.addEvent(EVENT) {
            setStringAttribute(ATTR_KEY, ATTR_VALUE)
        }
        val scope = otel.context.implicit().storeSpan(span).attach()
        otel.loggerProvider.getLogger(LOGGER_NAME).emit(body = LOG) {
            setStringAttribute(ATTR_KEY, ATTR_VALUE)
        }
        scope.detach()
        span.end()
    }

    /**
     * The Embrace API has no concept of a current span, so the log is emitted while the span is open.
     */
    private fun EmbraceActionInterface.recordEmbraceApiTelemetry() {
        val span = checkNotNull(embrace.startSpan(SPAN))
        span.addAttribute(ATTR_KEY, ATTR_VALUE)
        span.addEvent(EVENT, attributes = mapOf(ATTR_KEY to ATTR_VALUE))
        embrace.logMessage(LOG, Severity.INFO, mapOf(ATTR_KEY to ATTR_VALUE))
        span.stop()
    }

    private fun assertTelemetryExported(action: EmbraceActionInterface.() -> Unit) {
        testRule.runTest(
            testCaseAction = {
                recordSession { action() }
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == SPAN }.single()
                assertEquals(ATTR_VALUE, span.attributes.toStringMap()[ATTR_KEY])
                val event = span.events.single { it.name == EVENT }
                assertEquals(ATTR_VALUE, event.attributes.toStringMap()[ATTR_KEY])

                val log = awaitLogs(1) { it.bodyValue?.asString() == LOG }.single()
                assertEquals(ATTR_VALUE, log.attributes.toStringMap()[ATTR_KEY])
            },
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val TRACER_NAME = "interop-tracer"
        private const val LOGGER_NAME = "interop-logger"
        private const val SPAN = "span"
        private const val EVENT = "event"
        private const val LOG = "log"
        private const val ATTR_KEY = "attr-key"
        private const val ATTR_VALUE = "attr-value"
    }
}
