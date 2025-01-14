package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.payload.Envelope
import java.lang.reflect.Type

/**
 * Enumerates the different types of telemetry that are supported when persisting to disk.
 */
enum class SupportedEnvelopeType(
    val serializedType: Type?,
    val priority: String,
    val endpoint: Endpoint,
) {

    CRASH(Envelope.logEnvelopeType, "p1", Endpoint.LOGS),
    SESSION(Envelope.sessionEnvelopeType, "p3", Endpoint.SESSIONS),
    ATTACHMENT(null, "p4", Endpoint.ATTACHMENT),
    LOG(Envelope.logEnvelopeType, "p5", Endpoint.LOGS),
    BLOB(Envelope.logEnvelopeType, "p7", Endpoint.LOGS);

    companion object {
        private val valueMap =
            SupportedEnvelopeType.values().associateBy(SupportedEnvelopeType::priority)

        /**
         * Returns the [SupportedEnvelopeType] that corresponds to the given priority, if any.
         */
        fun fromPriority(priority: String): SupportedEnvelopeType? = valueMap[priority]
    }
}
