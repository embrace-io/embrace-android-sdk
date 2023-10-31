package io.embrace.android.embracesdk.comms.api

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

internal class EmbraceApiUrlBuilder(
    private val coreBaseUrl: String,
    private val configBaseUrl: String,
    private val appId: String,
    private val lazyDeviceId: Lazy<String>,
    context: Context,
) : ApiUrlBuilder {
    companion object {
        private const val API_VERSION = 1
        private const val CONFIG_API_VERSION = 2
    }

    private val appVersionName = try {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName.toString().trim { it <= ' ' }
        versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "UNKNOWN"
    }

    private fun getConfigBaseUrl() = buildUrl(configBaseUrl, CONFIG_API_VERSION, "config")

    private fun getOperatingSystemCode() = Build.VERSION.SDK_INT.toString() + ".0.0"

    private fun buildUrl(config: String, configApiVersion: Int, path: String): String {
        return "$config/v$configApiVersion/$path"
    }

    override fun getConfigUrl(): String {
        return "${getConfigBaseUrl()}?appId=$appId&osVersion=${getOperatingSystemCode()}" +
            "&appVersion=$appVersionName&deviceId=${lazyDeviceId.value}"
    }

    override fun getEmbraceUrlWithSuffix(suffix: String): String {
        InternalStaticEmbraceLogger.logDeveloper(
            "ApiUrlBuilder", "getEmbraceUrlWithSuffix - suffix: $suffix"
        )
        return "$coreBaseUrl/v$API_VERSION/log/$suffix"
    }
}
