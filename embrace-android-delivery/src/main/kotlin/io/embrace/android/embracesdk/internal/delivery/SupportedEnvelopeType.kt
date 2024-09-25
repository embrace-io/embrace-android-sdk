package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.payload.Envelope
import java.lang.reflect.Type

/**
 * Enumerates the different types of telemetry that are supported when persisting to disk.
 */
enum class SupportedEnvelopeType(
    val serializedType: Type,
    val description: String,
    val endpoint: Endpoint,
) {

    CRASH(Envelope.logEnvelopeType, "crash", Endpoint.LOGS),
    SESSION(Envelope.sessionEnvelopeType, "session", Endpoint.SESSIONS_V2),
    LOG(Envelope.logEnvelopeType, "log", Endpoint.LOGS),
    NETWORK(Envelope.logEnvelopeType, "network", Endpoint.LOGS);

    companion object {
        private val valueMap =
            SupportedEnvelopeType.values().associateBy(SupportedEnvelopeType::description)

        /**
         * Returns the [SupportedEnvelopeType] that corresponds to the given description, if any.
         */
        fun fromDescription(description: String): SupportedEnvelopeType? = valueMap[description]
    }
}
