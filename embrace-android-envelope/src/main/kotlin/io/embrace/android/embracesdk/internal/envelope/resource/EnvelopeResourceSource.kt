package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Creates a [EnvelopeResource] object.
 */
fun interface EnvelopeResourceSource {
    fun getEnvelopeResource(): EnvelopeResource
}
