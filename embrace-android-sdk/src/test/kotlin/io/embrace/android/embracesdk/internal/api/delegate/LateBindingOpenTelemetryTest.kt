package io.embrace.android.embracesdk.internal.api.delegate

import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.propagation.TextMapGetter
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.propagation.TextMapSetter
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LateBindingOpenTelemetryTest {

    @Test
    fun `propagator captured before start binds late`() {
        var started = false
        val fields = listOf("traceparent")
        val sdk = object : OpenTelemetry by NoopOpenTelemetry {
            override val propagator: TextMapPropagator = object : TextMapPropagator {
                override fun fields(): Collection<String> = fields
                override fun <T> inject(context: Context, carrier: T, setter: TextMapSetter<T>) = Unit
                override fun <T> extract(context: Context, carrier: T, getter: TextMapGetter<T>): Context = context
            }
        }
        val otel = LateBindingOpenTelemetry { if (started) sdk else NoopOpenTelemetry }
        val propagator = otel.propagator
        assertTrue(propagator.fields().isEmpty())

        started = true
        assertEquals(fields, propagator.fields())
    }

    @Test
    fun `a tracer taken before start gets its tracer once per backing OpenTelemetry`() {
        var current: OpenTelemetry = NoopOpenTelemetry
        val started = CountingOpenTelemetry()
        val disabled = CountingOpenTelemetry()
        val tracer = LateBindingOpenTelemetry { current }.tracerProvider.getTracer("test")

        current = started
        repeat(3) { startSpan(tracer) }
        assertEquals(1, started.tracersCreated)

        // a disable swaps the backing OpenTelemetry, which the tracer then follows
        current = disabled
        startSpan(tracer)
        assertEquals(1, started.tracersCreated)
        assertEquals(1, disabled.tracersCreated)
    }

    @Test
    fun `a logger taken before start gets its logger once per backing OpenTelemetry`() {
        var current: OpenTelemetry = NoopOpenTelemetry
        val started = CountingOpenTelemetry()
        val disabled = CountingOpenTelemetry()
        val logger = LateBindingOpenTelemetry { current }.loggerProvider.getLogger("test")

        current = started
        repeat(3) { emit(logger) }
        assertEquals(1, started.loggersCreated)

        current = disabled
        emit(logger)
        assertEquals(1, started.loggersCreated)
        assertEquals(1, disabled.loggersCreated)
    }

    private fun startSpan(tracer: Tracer) {
        tracer.startSpan(
            name = "span",
            parentContext = null,
            spanKind = SpanKind.INTERNAL,
            startTimestamp = null,
            action = null,
        )
    }

    private fun emit(logger: Logger) {
        logger.emit(
            body = "log",
            eventName = null,
            timestamp = null,
            observedTimestamp = null,
            context = null,
            severityNumber = null,
            severityText = null,
            exception = null,
            attributes = null,
        )
    }

    /**
     * Counts the tracers and loggers created from it, each a no-op.
     */
    private class CountingOpenTelemetry : OpenTelemetry by NoopOpenTelemetry {
        var tracersCreated = 0
        var loggersCreated = 0

        override val tracerProvider: TracerProvider = object : TracerProvider {
            override fun getTracer(
                name: String,
                version: String?,
                schemaUrl: String?,
                attributes: (AttributesMutator.() -> Unit)?,
            ): Tracer {
                tracersCreated++
                return NoopOpenTelemetry.tracerProvider.getTracer(name, version, schemaUrl, attributes)
            }
        }

        override val loggerProvider: LoggerProvider = object : LoggerProvider {
            override fun getLogger(
                name: String,
                version: String?,
                schemaUrl: String?,
                attributes: (AttributesMutator.() -> Unit)?,
            ): Logger {
                loggersCreated++
                return NoopOpenTelemetry.loggerProvider.getLogger(name, version, schemaUrl, attributes)
            }
        }
    }
}
