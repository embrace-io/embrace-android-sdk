package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

/**
 * Creates a [EnvelopeMetadata] object for Embrace API requests.
 */
fun interface EnvelopeMetadataSource {
    fun getEnvelopeMetadata(): EnvelopeMetadata
}
