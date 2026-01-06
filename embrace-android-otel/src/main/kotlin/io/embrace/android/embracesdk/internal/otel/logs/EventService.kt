package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber

/**
 * An OTel-agnostic API to create telemetry modeled as OTel LogRecords aka Events
 */
@OptIn(ExperimentalApi::class)
interface EventService : Initializable {

    /**
     * Records a log with Embrace-aware parameters
     */
    fun log(
        logTimeMs: Long,
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        addCurrentMetadata: Boolean
    )

    /**
     * Records a log using the given OTel Logger instance
     */
    fun logRecord(
        impl: Logger,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    )

    /**
     * Records an event using the given OTel Logger instance
     */
    fun logEvent(
        impl: Logger,
        eventName: String,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    )

    /**
     * Sets a provider that supplies a snapshot of the current metadata that describes the state of the SDK
     */
    fun setMetadataProvider(provider: Provider<Map<String, String>>)
}
