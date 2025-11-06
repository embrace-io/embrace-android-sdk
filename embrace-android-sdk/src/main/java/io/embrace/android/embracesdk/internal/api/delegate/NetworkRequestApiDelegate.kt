package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.instrumentation.network.DefaultTraceparentGenerator
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class NetworkRequestApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : NetworkRequestApi {

    private val configService by embraceImplInject(sdkCallChecker) { bootstrapper.configModule.configService }
    private val networkLoggingService by embraceImplInject(sdkCallChecker) {
        bootstrapper.logModule.networkLoggingService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionOrchestrationModule.sessionOrchestrator
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        if (sdkCallChecker.check("record_network_request")) {
            logNetworkRequest(networkRequest)
        }
    }

    override fun generateW3cTraceparent(): String? =
        if (configService?.networkSpanForwardingBehavior?.isNetworkSpanForwardingEnabled() == true) {
            DefaultTraceparentGenerator.generateW3cTraceparent()
        } else {
            null
        }

    private fun logNetworkRequest(request: EmbraceNetworkRequest) {
        if (configService?.networkBehavior?.isUrlEnabled(request.url) == true) {
            networkLoggingService?.logNetworkRequest(request)
            sessionOrchestrator?.onSessionDataUpdate()
        }
    }
}
