package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Creates a [EnvelopeResource] object.
 */
interface EnvelopeResourceSource {
    fun getEnvelopeResource(): EnvelopeResource
    fun add(key: String, value: String)
}
