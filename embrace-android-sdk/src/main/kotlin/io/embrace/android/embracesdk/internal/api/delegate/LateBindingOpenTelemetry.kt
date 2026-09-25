package io.embrace.android.embracesdk.internal.api.delegate

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.factory.BaggageFactory
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.factory.SpanContextFactory
import io.opentelemetry.kotlin.factory.SpanFactory
import io.opentelemetry.kotlin.factory.TraceFlagsFactory
import io.opentelemetry.kotlin.factory.TraceStateFactory
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.metrics.MeterProvider
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider

/**
 * Resolves the backing [OpenTelemetry] on every call, so tracers and loggers obtained before SDK start work after it.
 */
internal class LateBindingOpenTelemetry(
    private val resolve: () -> OpenTelemetry,
) : OpenTelemetry {

    override val tracerProvider: TracerProvider = object : TracerProvider {
        override fun getTracer(
            name: String,
            version: String?,
            schemaUrl: String?,
            attributes: (AttributesMutator.() -> Unit)?,
        ): Tracer = LateBindingTracer { resolve().tracerProvider.getTracer(name, version, schemaUrl, attributes) }
    }

    override val loggerProvider: LoggerProvider = object : LoggerProvider {
        override fun getLogger(
            name: String,
            version: String?,
            schemaUrl: String?,
            attributes: (AttributesMutator.() -> Unit)?,
        ): Logger = LateBindingLogger { resolve().loggerProvider.getLogger(name, version, schemaUrl, attributes) }
    }

    override val meterProvider: MeterProvider get() = resolve().meterProvider
    override val spanContext: SpanContextFactory get() = resolve().spanContext
    override val traceFlags: TraceFlagsFactory get() = resolve().traceFlags
    override val traceState: TraceStateFactory get() = resolve().traceState
    override val context: ContextFactory get() = resolve().context
    override val span: SpanFactory get() = resolve().span
    override val baggage: BaggageFactory get() = resolve().baggage
    override val propagator: TextMapPropagator get() = resolve().propagator
}

private class LateBindingTracer(private val resolve: () -> Tracer) : Tracer {

    override fun enabled(): Boolean = resolve().enabled()

    override fun startSpan(
        name: String,
        parentContext: Context?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: (SpanCreationAction.() -> Unit)?,
    ): Span = resolve().startSpan(name, parentContext, spanKind, startTimestamp, action)
}

private class LateBindingLogger(private val resolve: () -> Logger) : Logger {

    override fun enabled(
        context: Context?,
        severityNumber: SeverityNumber?,
        eventName: String?,
    ): Boolean = resolve().enabled(context, severityNumber, eventName)

    override fun emit(
        body: Any?,
        eventName: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        exception: Throwable?,
        attributes: (AttributesMutator.() -> Unit)?,
    ) = resolve().emit(
        body,
        eventName,
        timestamp,
        observedTimestamp,
        context,
        severityNumber,
        severityText,
        exception,
        attributes,
    )
}
