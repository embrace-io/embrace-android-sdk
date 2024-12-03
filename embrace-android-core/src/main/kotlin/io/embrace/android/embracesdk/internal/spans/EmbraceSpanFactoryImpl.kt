package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.opentelemetry.embraceSpanBuilder
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock

internal class EmbraceSpanFactoryImpl(
    private val tracer: Tracer,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private var sensitiveKeysBehavior: SensitiveKeysBehavior? = null,
) : EmbraceSpanFactory {

    override fun create(
        name: String,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
    ): PersistableEmbraceSpan = create(
        embraceSpanBuilder = tracer.embraceSpanBuilder(
            name = name,
            type = type,
            internal = internal,
            private = private,
            parent = parent,
            autoTerminationMode = autoTerminationMode
        )
    )

    override fun create(embraceSpanBuilder: EmbraceSpanBuilder): PersistableEmbraceSpan =
        EmbraceSpanImpl(
            spanBuilder = embraceSpanBuilder,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            sensitiveKeysBehavior = sensitiveKeysBehavior
        )

    override fun setupSensitiveKeysBehavior(sensitiveKeysBehavior: SensitiveKeysBehavior) {
        this.sensitiveKeysBehavior = sensitiveKeysBehavior
    }
}
