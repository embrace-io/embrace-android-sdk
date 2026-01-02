package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalApi::class)
class FakeEventService : EventService {
    val events: MutableList<EventData> = mutableListOf()
    val otelEvents: MutableList<OTelEventData> = mutableListOf()
    var initTime: Long? = null

    override val eventMetadataSupplierRef = AtomicReference<Provider<Map<String, String>>> { emptyMap() }

    override fun log(
        logTimeMs: Long,
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        embraceAttributes: Map<String, String>,
        addCurrentMetadata: Boolean,
    ) {
        events.add(
            EventData(
                logTimeMs = logTimeMs,
                schemaType = schemaType,
                severity = severity,
                message = message,
                isPrivate = isPrivate,
                attributes = embraceAttributes,
                addCurrentMetadata = addCurrentMetadata
            )
        )
    }

    override fun logRecord(
        impl: Logger,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        otelEvents.add(
            OTelEventData(
                logger = impl,
                eventName = null,
                body = body,
                timestamp = timestamp,
                observedTimestamp = observedTimestamp,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                attributes = attributes
            )
        )
    }

    override fun logEvent(
        impl: Logger,
        eventName: String,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        otelEvents.add(
            OTelEventData(
                logger = impl,
                eventName = eventName,
                body = body,
                timestamp = timestamp,
                observedTimestamp = observedTimestamp,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                attributes = attributes
            )
        )
    }

    override fun initializeService(sdkInitStartTimeMs: Long) {
        initTime = sdkInitStartTimeMs
    }

    override fun initialized(): Boolean = true

    data class EventData(
        val logTimeMs: Long,
        val schemaType: SchemaType,
        val severity: Severity,
        val message: String,
        val isPrivate: Boolean,
        val attributes: Map<String, String>,
        val addCurrentMetadata: Boolean,
    )

    data class OTelEventData(
        val logger: Logger,
        val eventName: String?,
        val body: String?,
        val timestamp: Long?,
        val observedTimestamp: Long?,
        val context: Context?,
        val severityNumber: SeverityNumber?,
        val severityText: String?,
        val attributes: (MutableAttributeContainer.() -> Unit)?,
    )
}
