package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock

internal class EmbraceSpanFactoryImpl(
    private val tracer: Tracer,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
) : EmbraceSpanFactory {

    override fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        parent: EmbraceSpan?
    ): PersistableEmbraceSpan = create(
        embraceSpanBuilder = tracer.embraceSpanBuilder(
            name = name,
            type = type,
            internal = internal,
            private = private,
            parent = parent,
        )
    )

    override fun create(embraceSpanBuilder: EmbraceSpanBuilder): PersistableEmbraceSpan =
        EmbraceSpanImpl(
            spanBuilder = embraceSpanBuilder,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository
        )
}
