package io.embrace.android.embracesdk.internal.api.delegate

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.factory.SpanFactory
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.metrics.MeterProvider
import io.opentelemetry.kotlin.propagation.TextMapGetter
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.propagation.TextMapSetter
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
    override val context: ContextFactory = LateBindingContextFactory { resolve().context }
    override val span: SpanFactory get() = resolve().span
    override val propagator: TextMapPropagator = LateBindingTextMapPropagator { resolve().propagator }
}

private class LateBindingContextFactory(private val resolve: () -> ContextFactory) : ContextFactory {
    override fun root(): Context = resolve().root()
    override fun implicit(): Context = resolve().implicit()
    override fun <T> createKey(name: String): ContextKey<T> = resolve().createKey(name)
}

private class LateBindingTextMapPropagator(private val resolve: () -> TextMapPropagator) : TextMapPropagator {
    override fun fields(): Collection<String> = resolve().fields()
    override fun <T> inject(context: Context, carrier: T?, setter: TextMapSetter<T>) =
        resolve().inject(context, carrier, setter)
    override fun <T> extract(context: Context, carrier: T?, getter: TextMapGetter<T>): Context =
        resolve().extract(context, carrier, getter)
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
