package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureService

class FakeNetworkCaptureService : NetworkCaptureService {

    val urls: MutableList<String> = mutableListOf()

    override fun getNetworkCaptureRules(
        url: String,
        method: String,
    ): Set<NetworkCaptureRuleRemoteConfig> {
        return emptySet()
    }

    override fun logNetworkRequest(
        request: HttpNetworkRequest
    ) {
        urls.add(request.url)
    }
}
