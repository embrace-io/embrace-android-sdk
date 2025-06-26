package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanCreator
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.k2j.tracing.TracerAdapter
import io.embrace.opentelemetry.kotlin.tracing.Tracer

/**
 * Creates a new [OtelSpanCreator] that marks the resulting span as private if [internal] is true
 */
@OptIn(ExperimentalApi::class)
fun Tracer.otelSpanCreator(
    name: String,
    type: EmbType,
    internal: Boolean,
    private: Boolean,
    parent: EmbraceSpan? = null,
    autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
): OtelSpanCreator = OtelSpanCreator(
    OtelSpanStartArgs(
        name = name,
        type = type,
        internal = internal,
        private = private,
        autoTerminationMode = autoTerminationMode,
        parentSpan = parent,
    ),
    tracer = this
)

/**
 * Creates a new [OtelSpanCreator] that marks the resulting span as private if [internal] is true
 */
@OptIn(ExperimentalApi::class)
fun OtelJavaTracer.otelSpanCreator(
    name: String,
    type: EmbType,
    internal: Boolean,
    private: Boolean,
    clock: Clock,
    parent: EmbraceSpan? = null,
    autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
): OtelSpanCreator = OtelSpanCreator(
    OtelSpanStartArgs(
        name = name,
        type = type,
        internal = internal,
        private = private,
        autoTerminationMode = autoTerminationMode,
        parentSpan = parent,
    ),
    tracer = TracerAdapter(this, clock)
)
