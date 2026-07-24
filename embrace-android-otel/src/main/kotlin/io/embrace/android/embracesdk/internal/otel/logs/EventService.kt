package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.Initializable
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber

/**
 * An OTel-agnostic API to create telemetry modeled as OTel LogRecords aka Events
 */
interface EventService : Initializable {
    /**
     * Records an event using the given OTel Logger instance. Defaults to the SDK instance if not provided
     */
    fun log(
        impl: Logger? = null,
        eventName: String?,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        eventAttributes: (AttributesMutator.() -> Unit)?,
    )

    /**
     * Sets the provider that supplies snapshots of the current metadata that describes the state
     * of the SDK, bundled by privacy scope.
     */
    fun setMetadataProvider(provider: EventMetadataProvider)
}
