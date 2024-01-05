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
        private const val API_VERSION = 1
        private const val CONFIG_API_VERSION = 2
    }

    private fun getConfigBaseUrl() = "$configBaseUrl/v$CONFIG_API_VERSION/${"config"}"

    private fun getOperatingSystemCode() = Build.VERSION.SDK_INT.toString() + ".0.0"

    override fun getConfigUrl(): String {
        return "${getConfigBaseUrl()}?appId=$appId&osVersion=${getOperatingSystemCode()}" +
            "&appVersion=${lazyAppVersionName.value}&deviceId=${lazyDeviceId.value}"
    }

    override fun getEmbraceUrlWithSuffix(suffix: String): String {
        InternalStaticEmbraceLogger.logDeveloper(
            "ApiUrlBuilder",
            "getEmbraceUrlWithSuffix - suffix: $suffix"
        )
        return "$coreBaseUrl/v$API_VERSION/log/$suffix"
    }
}
