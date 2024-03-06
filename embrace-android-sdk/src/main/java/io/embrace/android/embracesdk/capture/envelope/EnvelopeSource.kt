package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.internal.payload.Envelope

internal fun interface EnvelopeSource<T> {
    fun getEnvelope(): Envelope<T>
}
