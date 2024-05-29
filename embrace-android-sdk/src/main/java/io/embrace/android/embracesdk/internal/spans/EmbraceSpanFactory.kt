package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock

/**
 * Creates instances of [PersistableEmbraceSpan] for internal usage. Using this factory is preferred to invoking the constructor
 * because of the it requires several services that may not be easily available.
 */
internal interface EmbraceSpanFactory {
    fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean = internal,
        parent: EmbraceSpan? = null
    ): PersistableEmbraceSpan
}

internal class EmbraceSpanFactoryImpl(
    private val tracer: Tracer,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private val serializer: PlatformSerializer,
) : EmbraceSpanFactory {

    override fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        parent: EmbraceSpan?
    ): PersistableEmbraceSpan = EmbraceSpanImpl(
        spanBuilder = tracer.embraceSpanBuilder(
            name = name,
            type = type,
            internal = internal,
            private = private,
            parent = parent
        ),
        openTelemetryClock = openTelemetryClock,
        spanRepository = spanRepository,
        serializer = serializer
    )
}
