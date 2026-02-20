package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
class EmbLogger(
    private val impl: Logger,
    private val eventService: EventService,
) : Logger {
    override fun enabled(
        context: Context?,
        severityNumber: SeverityNumber?,
        eventName: String?,
    ): Boolean = true

    override fun emit(
        body: String?,
        eventName: String?,
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

    @Deprecated(
        "Use emit() instead",
        replaceWith = ReplaceWith("emit(body, null, timestamp, observedTimestamp, context, severityNumber, severityText, attributes)")
    )
    override fun log(
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        emit(
            body = body,
            eventName = null,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            attributes = attributes
        )
    }

    @Deprecated(
        "Use emit() instead",
        replaceWith = ReplaceWith("emit(body, eventName, timestamp, observedTimestamp, context, severityNumber, severityText, attributes)")
    )
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
        emit(
            body = body,
            eventName = eventName,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            attributes = attributes
        )
    }
}
