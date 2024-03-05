package io.embrace.android.embracesdk.capture.envelope.metadata

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

/**
 * Creates a [EnvelopeMetadata] object for Embrace API requests.
 */
internal fun interface EnvelopeMetadataSource {
    fun getEnvelopeMetadata(): EnvelopeMetadata
}
