package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber

class FakeOpenTelemetryLogger : Logger {

    val logs: MutableList<FakeLogRecord> = mutableListOf()

    override fun enabled(
        context: Context?,
        severityNumber: SeverityNumber?,
        eventName: String?,
    ): Boolean = true

    override fun emit(
        body: Any?,
        eventName: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        exception: Throwable?,
        attributes: (AttributesMutator.() -> Unit)?,
    ) {
        processTelemetry(
            eventName = eventName,
            body = body?.toString(),
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            attributes = attributes
        )
    }

    private fun processTelemetry(
        eventName: String?,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (AttributesMutator.() -> Unit)?,
    ) {
        val container = FakeAttributesMutator()
        if (attributes != null) {
            attributes(container)
        }
        logs.add(
            FakeLogRecord(
                eventName = eventName,
                body = body,
                timestamp = timestamp,
                observedTimestamp = observedTimestamp,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                attributes = container.attributes
            )
        )
    }
}
