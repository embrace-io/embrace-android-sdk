package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
class FakeOpenTelemetryLogger : Logger {

    val logs: MutableList<FakeLogRecord> = mutableListOf()

    override fun log(
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        processTelemetry(attributes, null, body, timestamp, observedTimestamp, context, severityNumber, severityText)
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
        processTelemetry(attributes, eventName, body, timestamp, observedTimestamp, context, severityNumber, severityText)
    }

    private fun processTelemetry(
        attributes: (MutableAttributeContainer.() -> Unit)?,
        eventName: String?,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
    ) {
        val container = FakeMutableAttributeContainer()
        if (attributes != null) {
            attributes(container)
        }
        logs.add(
            FakeLogRecord(
                body = body,
                eventName = eventName,
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
