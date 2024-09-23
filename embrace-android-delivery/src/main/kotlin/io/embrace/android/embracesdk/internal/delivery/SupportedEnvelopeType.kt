package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.payload.Envelope
import java.lang.reflect.Type

/**
 * Enumerates the different types of telemetry that are supported when persisting to disk.
 */
enum class SupportedEnvelopeType(
    val serializedType: Type,
    val description: String
) {

    CRASH(Envelope.logEnvelopeType, "crash"),
    SESSION(Envelope.sessionEnvelopeType, "session"),
    LOG(Envelope.logEnvelopeType, "log"),
    NETWORK(Envelope.logEnvelopeType, "network");

    companion object {
        private val valueMap =
            SupportedEnvelopeType.values().associateBy(SupportedEnvelopeType::description)

        /**
         * Returns the [SupportedEnvelopeType] that corresponds to the given description, if any.
         */
        fun fromDescription(description: String): SupportedEnvelopeType? = valueMap[description]
    }
}
