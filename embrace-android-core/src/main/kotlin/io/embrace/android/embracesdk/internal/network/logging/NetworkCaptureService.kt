package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData

public interface NetworkCaptureService {

    /**
     * Returns the network capture rule applicable to the URL and the method given.
     *
     * @param url the network URL
     * @param method the network URL
     * @return the network rule to apply, or null if it is no rule that match the criteria.
     */
    public fun getNetworkCaptureRules(url: String, method: String): Set<NetworkCaptureRuleRemoteConfig>

    /**
     * Logs the network captured data if this match the rule criteria.
     */
    public fun logNetworkCapturedData(
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        networkCaptureData: NetworkCaptureData?,
        errorMessage: String? = null
    )
}
