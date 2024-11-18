package io.embrace.android.embracesdk.internal.comms.api

import android.os.Build
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

internal class EmbraceApiUrlBuilder(
    override val deviceId: String,
    private val appVersionName: String,
    instrumentedConfig: InstrumentedConfig,
) : ApiUrlBuilder {

    companion object {
        private const val CONFIG_DEFAULT: String = "config.emb-api.com"
        private const val DATA_DEFAULT: String = "data.emb-api.com"
    }

    override val appId: String = checkNotNull(instrumentedConfig.project.getAppId())
    private val coreBaseUrl = instrumentedConfig.baseUrls.getData() ?: "https://a-$appId.$DATA_DEFAULT"
    private val configBaseUrl = instrumentedConfig.baseUrls.getConfig() ?: "https://a-$appId.$CONFIG_DEFAULT"
    private val operatingSystemCode = Build.VERSION.SDK_INT.toString() + ".0.0"
    override val baseDataUrl: String = resolveUrl(Endpoint.SESSIONS).split(Endpoint.SESSIONS.path).first()

    override fun resolveUrl(endpoint: Endpoint): String {
        val baseUrl = when (endpoint) {
            Endpoint.CONFIG -> configBaseUrl
            else -> coreBaseUrl
        }
        val queryParams = when (endpoint) {
            Endpoint.CONFIG ->
                "?appId=$appId&osVersion=$operatingSystemCode" +
                    "&appVersion=$appVersionName&deviceId=$deviceId"
            else -> ""
        }
        return "$baseUrl/${endpoint.version}/${endpoint.path}$queryParams"
    }
}
