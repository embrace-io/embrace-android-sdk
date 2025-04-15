package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber

@OptIn(ExperimentalApi::class)
class FakeOtelLogger : Logger {
    override fun log(
        body: String?,
        timestampNs: Long?,
        observedTimestampNs: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: AttributeContainer.() -> Unit,
    ) {
    }
}
