package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class FakeEnvelopeResourceSource : EnvelopeResourceSource {

    var resource: EnvelopeResource = EnvelopeResource()

    override fun getEnvelopeResource(): EnvelopeResource = resource
}
