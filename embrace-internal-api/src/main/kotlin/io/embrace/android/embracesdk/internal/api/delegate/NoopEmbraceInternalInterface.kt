package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal object NoopEmbraceInternalInterface : EmbraceInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean = false

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = false

    override fun logInternalError(error: Throwable) {}

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
    }
}
