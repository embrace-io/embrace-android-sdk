package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
class FakeEventService : EventService {
    val eventData: MutableList<FakeEventData> = mutableListOf()
    var initTime: Long? = null

    override fun log(
        impl: Logger?,
        eventName: String?,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        eventAttributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        eventData.add(
            FakeEventData(
                logger = impl,
                eventName = eventName,
                body = body,
                timestamp = timestamp,
                observedTimestamp = observedTimestamp,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                addCurrentMetadata = addCurrentMetadata,
                attributes = eventAttributes
            )
        )
    }

    override fun setMetadataProvider(provider: Provider<Map<String, String>>) {
    }

    override fun initializeService(sdkInitStartTimeMs: Long) {
        initTime = sdkInitStartTimeMs
    }

    override fun initialized(): Boolean = true

    data class FakeEventData(
        val logger: Logger?,
        val eventName: String?,
        val body: String?,
        val timestamp: Long?,
        val observedTimestamp: Long?,
        val context: Context?,
        val severityNumber: SeverityNumber?,
        val severityText: String?,
        val addCurrentMetadata: Boolean,
        val attributes: (MutableAttributeContainer.() -> Unit)?,
    )
}
