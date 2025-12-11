package io.embrace.android.embracesdk.internal.api.delegate

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

@SuppressLint("EmbracePublicApiPackageRule")
internal class NoopEmbraceInternalInterface(
    internalTracer: InternalTracingApi,
) : EmbraceInternalInterface, InternalTracingApi by internalTracer {

    override fun isNetworkSpanForwardingEnabled(): Boolean = false

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = false

    override fun logInternalError(message: String?, details: String?) {}

    override fun logInternalError(error: Throwable) {}

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
    }
    override fun getRemoteConfig(): Map<String, Any>? = null

    override fun isConfigFeatureEnabled(pctEnabled: Float?): Boolean? = false
}
