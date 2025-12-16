package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

internal object NoopEmbraceInternalInterface : EmbraceInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean = false

    override fun addEnvelopeResource(key: String, value: String) {
    }
}
