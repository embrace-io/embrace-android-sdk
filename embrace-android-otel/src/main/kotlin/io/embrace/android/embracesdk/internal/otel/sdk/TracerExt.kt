package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.Tracer

/**
 * Creates a new [OtelSpanStartArgs] that marks the resulting span as private if [internal] is true
 */
@OptIn(ExperimentalApi::class)
fun Tracer.otelSpanArgs(
    name: String,
    type: EmbType,
    internal: Boolean,
    private: Boolean,
    parent: EmbraceSpan? = null,
    autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
): OtelSpanStartArgs = OtelSpanStartArgs(
    name = name,
    type = type,
    internal = internal,
    private = private,
    autoTerminationMode = autoTerminationMode,
    tracer = this,
    parentSpan = parent,
)
