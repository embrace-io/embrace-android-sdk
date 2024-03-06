package io.embrace.android.embracesdk.comms.api

import android.os.Build
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

internal class EmbraceApiUrlBuilder(
    private val coreBaseUrl: String,
    private val configBaseUrl: String,
    private val appId: String,
    private val lazyDeviceId: Lazy<String>,
    private val lazyAppVersionName: Lazy<String>
) : ApiUrlBuilder {
    companion object {
        private const val CONFIG_API_VERSION = 2
    }

    private fun getConfigBaseUrl() = "$configBaseUrl/v$CONFIG_API_VERSION/${"config"}"

    private fun getOperatingSystemCode() = Build.VERSION.SDK_INT.toString() + ".0.0"

    override fun getConfigUrl(): String {
        return "${getConfigBaseUrl()}?appId=$appId&osVersion=${getOperatingSystemCode()}" +
            "&appVersion=${lazyAppVersionName.value}&deviceId=${lazyDeviceId.value}"
    }

    override fun getEmbraceUrlWithSuffix(apiVersion: String, suffix: String): String {
        InternalStaticEmbraceLogger.logDeveloper(
            "ApiUrlBuilder",
            "getEmbraceUrlWithSuffix - apiVersion: $apiVersion - suffix: $suffix"
        )
        val fullSuffix = if (apiVersion == "v1") "log/$suffix" else suffix
        return "$coreBaseUrl/$apiVersion/$fullSuffix"
    }
}
