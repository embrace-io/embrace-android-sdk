package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.otel.java.getJavaOpenTelemetry
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.opentelemetry.kotlin.aliases.OtelJavaAttributes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sanity checks that each API surface exports spans and logs on both the Java and Kotlin SDK implementations.
 *
 * A span and log are created using the Embrace, opentelemetry-java, and opentelemetry-kotlin APIs. An additional
 * dimension is supplied via `useKotlinSdk`, which controls whether the 'compat' or 'regular' mode of
 * opentelemetry-kotlin is used.
 *
 * Assertions verify the exported OTLP is the same, barring minor differences.
 */
@RunWith(AndroidJUnit4::class)
internal class OtelApiInteropTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `java api with java sdk`() {
        assertTelemetryExported(useKotlinSdk = false) { recordJavaApiTelemetry() }
    }

    @Test
    fun `java api with kotlin sdk`() {
        assertTelemetryExported(useKotlinSdk = true) { recordJavaApiTelemetry() }
    }

    @Test
    fun `kotlin api with java sdk`() {
        assertTelemetryExported(useKotlinSdk = false) { recordKotlinApiTelemetry() }
    }

    @Test
    fun `kotlin api with kotlin sdk`() {
        assertTelemetryExported(useKotlinSdk = true) { recordKotlinApiTelemetry() }
    }

    @Test
    fun `embrace api with java sdk`() {
        assertTelemetryExported(useKotlinSdk = false) { recordEmbraceApiTelemetry() }
    }

    @Test
    fun `embrace api with kotlin sdk`() {
        assertTelemetryExported(useKotlinSdk = true) { recordEmbraceApiTelemetry() }
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

    private fun assertTelemetryExported(useKotlinSdk: Boolean, action: EmbraceActionInterface.() -> Unit) {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = if (useKotlinSdk) 100.0f else 0.0f),
            ),
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

    private companion object {
        private const val TRACER_NAME = "interop-tracer"
        private const val LOGGER_NAME = "interop-logger"
        private const val SPAN = "span"
        private const val EVENT = "event"
        private const val LOG = "log"
        private const val ATTR_KEY = "attr-key"
        private const val ATTR_VALUE = "attr-value"
    }
}
