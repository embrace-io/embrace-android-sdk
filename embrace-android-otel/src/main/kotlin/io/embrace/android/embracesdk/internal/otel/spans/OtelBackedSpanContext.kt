package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.context.TraceFlags
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.kotlin.tracing.createSpanContext

/**
 * Embrace [io.embrace.android.embracesdk.context.SpanContext] backed by an OTel [SpanContext].
 */
internal class OtelBackedSpanContext(val otel: SpanContext) : io.embrace.android.embracesdk.context.SpanContext {
    override val traceId: String get() = otel.traceId
    override val traceIdBytes: ByteArray get() = otel.traceIdBytes
    override val spanId: String get() = otel.spanId
    override val spanIdBytes: ByteArray get() = otel.spanIdBytes
    override val traceFlags: TraceFlags get() = traceFlagsOf(otel.traceFlags.isSampled, otel.traceFlags.isRandom)
    override val isValid: Boolean get() = otel.isValid
    override val isRemote: Boolean get() = otel.isRemote
    override val traceState: io.embrace.android.embracesdk.context.TraceState get() = OtelBackedTraceState(otel.traceState)

    override fun equals(other: Any?): Boolean = other is OtelBackedSpanContext && other.otel == otel
    override fun hashCode(): Int = otel.hashCode()
}

/**
 * Embrace [io.embrace.android.embracesdk.context.TraceState] backed by an OTel [TraceState].
 */
internal class OtelBackedTraceState(val otel: TraceState) : io.embrace.android.embracesdk.context.TraceState {
    override fun get(key: String): String? = otel.get(key)
    override fun asMap(): Map<String, String> = otel.asMap()

    override fun equals(other: Any?): Boolean = other is OtelBackedTraceState && other.otel == otel
    override fun hashCode(): Int = otel.hashCode()
    override fun toString(): String = "TraceState(${otel.asMap()})"
}

private data class TraceFlagsImpl(
    override val isSampled: Boolean,
    override val isRandom: Boolean,
) : TraceFlags

// immutable, so the 4 possible permutations are shared
private val TRACE_FLAGS: Array<TraceFlags> = Array(4) {
    TraceFlagsImpl(isSampled = (it and 1) != 0, isRandom = (it and 2) != 0)
}

internal fun traceFlagsOf(isSampled: Boolean, isRandom: Boolean): TraceFlags =
    TRACE_FLAGS[(if (isSampled) 1 else 0) or (if (isRandom) 2 else 0)]

fun SpanContext.toEmbraceSpanContext(): io.embrace.android.embracesdk.context.SpanContext = OtelBackedSpanContext(this)

fun io.embrace.android.embracesdk.context.SpanContext.toOtelSpanContext(): SpanContext = when (this) {
    is OtelBackedSpanContext -> otel
    else -> {
        val source = this
        createSpanContext(traceIdBytes, spanIdBytes) {
            isSampled = source.traceFlags.isSampled
            isRandom = source.traceFlags.isRandom
            isRemote = source.isRemote
            source.traceState.asMap().takeIf { it.isNotEmpty() }?.let { entries ->
                traceState { entries.forEach { (key, value) -> put(key, value) } }
            }
        }
    }
}
