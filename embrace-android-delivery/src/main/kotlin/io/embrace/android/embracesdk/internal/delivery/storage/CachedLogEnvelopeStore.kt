package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload

/**
 * Create and make available [Envelope] objects with custom [EnvelopeResource] and [EnvelopeMetadata] that differ from
 * the values used by the current SDK instance so telemetry from past app launches can be created and sent.
 */
interface CachedLogEnvelopeStore {

    /**
     * Create the envelope to be used for the given [StoredTelemetryMetadata] using the given
     * [EnvelopeResource] and [EnvelopeMetadata]
     */
    fun create(
        storedTelemetryMetadata: StoredTelemetryMetadata,
        resource: EnvelopeResource,
        metadata: EnvelopeMetadata,
    )

    /**
     * Return the envelope to be used for the given [StoredTelemetryMetadata] if it exists
     */
    fun get(storedTelemetryMetadata: StoredTelemetryMetadata): Envelope<LogPayload>?

    /**
     * Delete all cached envelopes. This should be called when all pending log payloads from previous SDK instances
     * have been created
     */
    fun clear()

    companion object {
        fun createNativeCrashEnvelopeMetadata(
            sessionId: String? = null,
            processIdentifier: String? = null,
        ) = StoredTelemetryMetadata(
            timestamp = 0L,
            uuid = sessionId ?: "none",
            processId = processIdentifier ?: "none",
            envelopeType = SupportedEnvelopeType.CRASH,
            payloadType = PayloadType.NATIVE_CRASH,
        )
    }
}
