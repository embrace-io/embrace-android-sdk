package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.IdGenerator
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class NetworkRequestApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
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

    override val traceIdHeader: String
        get() {
            if (sdkCallChecker.check("get_trace_id_header")) {
                return configService?.networkBehavior?.getTraceIdHeader()
                    ?: NetworkBehaviorImpl.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
            }
            return NetworkBehaviorImpl.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
        }

    override fun generateW3cTraceparent(): String? =
        if (configService?.networkSpanForwardingBehavior?.isNetworkSpanForwardingEnabled() == true) {
            IdGenerator.generateW3CTraceparent()
        } else {
            null
        }

    private fun logNetworkRequest(request: EmbraceNetworkRequest) {
        if (configService?.networkBehavior?.isUrlEnabled(request.url) == true) {
            networkLoggingService?.logNetworkRequest(request)
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
