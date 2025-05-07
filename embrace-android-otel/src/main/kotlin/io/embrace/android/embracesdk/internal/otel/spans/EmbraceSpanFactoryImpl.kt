package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.schema.TelemetryType
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanBuilderWrapper
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.sdk.common.Clock

@OptIn(ExperimentalApi::class)
class EmbraceSpanFactoryImpl(
    private val tracer: Tracer,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private var redactionFunction: ((key: String, value: String) -> String)? = null,
) : EmbraceSpanFactory {

    override fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
    ): EmbraceSdkSpan = create(
        otelSpanBuilderWrapper = tracer.otelSpanBuilderWrapper(
            name = name,
            type = type,
            internal = internal,
            private = private,
            parent = parent,
            autoTerminationMode = autoTerminationMode
        )
    )

    override fun create(otelSpanBuilderWrapper: OtelSpanBuilderWrapper): EmbraceSdkSpan =
        EmbraceSpanImpl(
            otelSpanBuilderWrapper = otelSpanBuilderWrapper,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            redactionFunction = redactionFunction
        )

    override fun setRedactionFunction(redactionFunction: (key: String, value: String) -> String) {
        this.redactionFunction = redactionFunction
    }
}
