package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

class FakeEnvelopeMetadataSource : EnvelopeMetadataSource {

    var metadata: EnvelopeMetadata = EnvelopeMetadata()

    var readCount: Int = 0

    override fun getEnvelopeMetadata(): EnvelopeMetadata {
        readCount++
        return metadata
    }
}
