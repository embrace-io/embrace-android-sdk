package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Creates a [EnvelopeResource] object.
 */
interface EnvelopeResourceSource {
    fun getEnvelopeResource(): EnvelopeResource
    fun add(key: String, value: String)

    /**
     * Registers a [listener] that is invoked whenever the [EnvelopeResource] changes. Most of the
     * resource is fixed for the lifetime of the process, but some values can mutate, so the
     * 'immutable' resource is actually mutable.
     *
     * The listener is invoked with the current resource upon registration.
     */
    fun addChangeListener(listener: (EnvelopeResource) -> Unit)
}
