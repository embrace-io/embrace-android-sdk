package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.IdGenerator
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class NetworkRequestApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : NetworkRequestApi {

    private val configService by embraceImplInject(sdkCallChecker) { bootstrapper.essentialServiceModule.configService }
    private val networkLoggingService by embraceImplInject(sdkCallChecker) {
        bootstrapper.customerLogModule.networkLoggingService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        if (sdkCallChecker.check("record_network_request")) {
            logNetworkRequest(networkRequest)
        }
    }

    override val traceIdHeader: String
        get() {
            if (sdkCallChecker.check("get_trace_id_header")) {
                return configService?.networkBehavior?.getTraceIdHeader()
                    ?: NetworkBehavior.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
            }
            return NetworkBehavior.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
        }

    override fun generateW3cTraceparent(): String = IdGenerator.generateW3CTraceparent()

    private fun logNetworkRequest(request: EmbraceNetworkRequest) {
        if (configService?.networkBehavior?.isUrlEnabled(request.url) == true) {
            networkLoggingService?.logNetworkRequest(request)
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
