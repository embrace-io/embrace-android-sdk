package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

class ConfigEndpoint(
    val deviceId: String,
    private val appVersionName: String,
    instrumentedConfig: InstrumentedConfig,
    val sdkVersion: String,
    apiLevel: Int,
) {

    val appId: String = checkNotNull(instrumentedConfig.project.getAppId())
    private val baseUrl = instrumentedConfig.baseUrls.getConfig() ?: "https://a-$appId.config.emb-api.com"
    private val operatingSystemCode = "$apiLevel.0.0"

    val url: String = run {
        val endpoint = Endpoint.CONFIG
        val queryParams =
            "?appId=$appId&osVersion=$operatingSystemCode" +
                "&appVersion=$appVersionName&deviceId=$deviceId"
        "$baseUrl/${endpoint.version}/${endpoint.path}$queryParams"
    }
}
