package io.embrace.android.embracesdk.internal.capture.envelope.resource

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Creates a [EnvelopeResource] object.
 */
internal fun interface EnvelopeResourceSource {
    fun getEnvelopeResource(): EnvelopeResource
}
