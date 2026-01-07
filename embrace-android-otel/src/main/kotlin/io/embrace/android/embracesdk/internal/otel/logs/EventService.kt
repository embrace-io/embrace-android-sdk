package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.Initializable
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
        eventAttributes: (MutableAttributeContainer.() -> Unit)?,
    )

    /**
     * Sets a provider that supplies a snapshot of the current metadata that describes the state of the SDK
     */
    fun setMetadataProvider(provider: Provider<Map<String, String>>)
}
