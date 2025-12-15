package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

class FakeEnvelopeResourceSource : EnvelopeResourceSource {

    var resource: EnvelopeResource = EnvelopeResource()
    var customValues = mutableMapOf<String, String>()

    override fun getEnvelopeResource(): EnvelopeResource = resource

    override fun add(key: String, value: String) {
        customValues[key] = value
    }
}
