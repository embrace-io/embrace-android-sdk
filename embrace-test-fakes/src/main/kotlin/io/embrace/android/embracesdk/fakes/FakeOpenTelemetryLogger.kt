package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber

@OptIn(ExperimentalApi::class)
class FakeOpenTelemetryLogger : Logger {

    val logs: MutableList<FakeLogRecord> = mutableListOf()

    override fun log(
        body: String?,
        timestampNs: Long?,
        observedTimestampNs: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: AttributeContainer.() -> Unit,
    ) {
        val container = FakeAttributeContainer()
        attributes(container)
        logs.add(
            FakeLogRecord(
                body = body,
                timestampNs = timestampNs,
                observedTimestampNs = observedTimestampNs,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                attrs = container.attributes()
            )
        )
    }
}
