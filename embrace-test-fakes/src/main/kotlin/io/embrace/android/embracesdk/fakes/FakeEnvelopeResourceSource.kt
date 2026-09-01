package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

class FakeEnvelopeResourceSource : EnvelopeResourceSource {

    var resource: EnvelopeResource = EnvelopeResource()
    var customValues = mutableMapOf<String, String>()
    val listeners = mutableListOf<(EnvelopeResource) -> Unit>()

    override fun getEnvelopeResource(): EnvelopeResource = resource

    override fun add(key: String, value: String) {
        customValues[key] = value
    }

    override fun addChangeListener(listener: (EnvelopeResource) -> Unit) {
        listeners.add(listener)
        listener(resource)
    }

    fun changeResource(resource: EnvelopeResource) {
        this.resource = resource
        listeners.forEach { it(resource) }
    }
}
