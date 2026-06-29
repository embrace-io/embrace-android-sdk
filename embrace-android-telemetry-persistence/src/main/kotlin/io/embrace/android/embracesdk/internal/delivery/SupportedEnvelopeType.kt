package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.payload.Envelope
import kotlinx.serialization.KSerializer

/**
 * Enumerates the different types of telemetry that are supported when persisting to disk.
 */
@Suppress("UNCHECKED_CAST")
enum class SupportedEnvelopeType(
    val envelopeSerializer: KSerializer<Envelope<*>>?,
    val priority: String,
    val endpoint: Endpoint,
) {

    CRASH(Envelope.logEnvelopeSerializer as KSerializer<Envelope<*>>, "p1", Endpoint.LOGS),
    SESSION(Envelope.sessionEnvelopeSerializer as KSerializer<Envelope<*>>, "p3", Endpoint.SESSIONS),
    ATTACHMENT(null, "p4", Endpoint.ATTACHMENTS),
    LOG(Envelope.logEnvelopeSerializer as KSerializer<Envelope<*>>, "p5", Endpoint.LOGS),
    BLOB(Envelope.logEnvelopeSerializer as KSerializer<Envelope<*>>, "p7", Endpoint.LOGS),
    ;

    companion object {
        private val valueMap =
            SupportedEnvelopeType.entries.associateBy(SupportedEnvelopeType::priority)

        /**
         * Returns the [SupportedEnvelopeType] that corresponds to the given priority, if any.
         */
        fun fromPriority(priority: String): SupportedEnvelopeType? = valueMap[priority]
    }
}
