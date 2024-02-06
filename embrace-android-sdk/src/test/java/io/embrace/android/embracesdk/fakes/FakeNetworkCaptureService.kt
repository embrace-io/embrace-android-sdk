package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService

internal class FakeNetworkCaptureService : NetworkCaptureService {

    val urls: MutableList<String> = mutableListOf()

    override fun getNetworkCaptureRules(
        url: String,
        method: String
    ): Set<NetworkCaptureRuleRemoteConfig> {
        return emptySet()
    }

    override fun logNetworkCapturedData(
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        networkCaptureData: NetworkCaptureData?,
        errorMessage: String?
    ) {
        urls.add(url)
    }
}
