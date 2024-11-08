package io.embrace.android.embracesdk.internal.comms.api

import android.os.Build

internal class EmbraceApiUrlBuilder(
    private val coreBaseUrl: String,
    private val configBaseUrl: String,
    override val appId: String,
    deviceIdImpl: Lazy<String>,
    private val lazyAppVersionName: Lazy<String>,
) : ApiUrlBuilder {

    companion object {
        private const val CONFIG_API_VERSION = 2
    }

    override val deviceId: String by deviceIdImpl

    private fun getConfigBaseUrl() = "$configBaseUrl/v$CONFIG_API_VERSION/${"config"}"

    private fun getOperatingSystemCode() = Build.VERSION.SDK_INT.toString() + ".0.0"

    override fun getConfigUrl(): String {
        return "${getConfigBaseUrl()}?appId=$appId&osVersion=${getOperatingSystemCode()}" +
            "&appVersion=${lazyAppVersionName.value}&deviceId=$deviceId"
    }

    override fun getEmbraceUrlWithSuffix(apiVersion: String, suffix: String): String {
        val fullSuffix = if (apiVersion == "v1") "log/$suffix" else suffix
        return "$coreBaseUrl/$apiVersion/$fullSuffix"
    }
}
