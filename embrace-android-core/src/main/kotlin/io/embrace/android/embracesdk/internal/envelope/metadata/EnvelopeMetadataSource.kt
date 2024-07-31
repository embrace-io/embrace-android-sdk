package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

/**
 * Creates a [EnvelopeMetadata] object for Embrace API requests.
 */
public fun interface EnvelopeMetadataSource {
    public fun getEnvelopeMetadata(): EnvelopeMetadata
}
