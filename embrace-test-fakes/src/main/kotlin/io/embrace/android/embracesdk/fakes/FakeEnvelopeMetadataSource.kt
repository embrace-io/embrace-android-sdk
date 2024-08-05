package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

public class FakeEnvelopeMetadataSource : EnvelopeMetadataSource {

    public var metadata: EnvelopeMetadata = EnvelopeMetadata()

    override fun getEnvelopeMetadata(): EnvelopeMetadata = metadata
}
