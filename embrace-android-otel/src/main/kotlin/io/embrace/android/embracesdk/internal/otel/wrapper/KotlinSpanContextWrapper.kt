package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceFlags
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceState
import io.embrace.opentelemetry.kotlin.tracing.SpanContext

class KotlinSpanContextWrapper(
    private val impl: SpanContext,
) : OtelJavaSpanContext {

    override fun getTraceId(): String = impl.traceId
    override fun getSpanId(): String = impl.spanId
    override fun isRemote(): Boolean = impl.isRemote

    override fun getTraceFlags(): OtelJavaTraceFlags = KotlinTraceFlagsWrapper(impl.traceFlags)
    override fun getTraceState(): OtelJavaTraceState = KotlinTraceStateWrapper(impl.traceState)
}
