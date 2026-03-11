package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.model.SeverityNumber

class EmbLogger(
    private val impl: Logger,
    private val eventService: EventService,
) : Logger {
    override fun enabled(
        context: Context?,
        severityNumber: SeverityNumber?,
        eventName: String?,
    ): Boolean = impl.enabled(context, severityNumber, eventName)

    override fun emit(
        body: String?,
        eventName: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (AttributesMutator.() -> Unit)?,
    ) {
        eventService.log(
            impl = impl,
            eventName = eventName,
            body = body,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            addCurrentMetadata = true,
            eventAttributes = attributes
        )
    }
}
