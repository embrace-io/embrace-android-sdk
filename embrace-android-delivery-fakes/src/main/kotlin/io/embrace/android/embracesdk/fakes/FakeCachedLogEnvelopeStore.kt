package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload

class FakeCachedLogEnvelopeStore : CachedLogEnvelopeStore {
    val envelopeGetRequest: MutableList<StoredTelemetryMetadata> = mutableListOf()
    val createdEnvelopes: MutableList<Envelope<LogPayload>> = mutableListOf()
    private val currentEnvelopes: MutableMap<StoredTelemetryMetadata, Envelope<LogPayload>> = mutableMapOf()

    override fun create(
        storedTelemetryMetadata: StoredTelemetryMetadata,
        resource: EnvelopeResource,
        metadata: EnvelopeMetadata,
    ) {
        val envelope = fakeEmptyLogEnvelope(resource = resource, metadata = metadata)
        createdEnvelopes.add(envelope)
        currentEnvelopes[storedTelemetryMetadata] = envelope
    }

    override fun get(storedTelemetryMetadata: StoredTelemetryMetadata): Envelope<LogPayload>? {
        val envelope = currentEnvelopes[storedTelemetryMetadata]
        envelopeGetRequest.add(storedTelemetryMetadata)
        return envelope
    }

    override fun clear() = currentEnvelopes.clear()
}
