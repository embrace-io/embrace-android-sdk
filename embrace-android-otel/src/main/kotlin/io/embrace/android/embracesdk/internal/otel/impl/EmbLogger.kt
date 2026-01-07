package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
class EmbLogger(
    private val impl: Logger,
    private val eventService: EventService,
) : Logger {
    override fun log(
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        eventService.log(
            impl = impl,
            eventName = null,
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

    override fun logEvent(
        eventName: String,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
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
