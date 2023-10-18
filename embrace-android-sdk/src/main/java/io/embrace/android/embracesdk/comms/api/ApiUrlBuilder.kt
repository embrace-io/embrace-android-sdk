package io.embrace.android.embracesdk.comms.api

import android.os.Build
import android.os.Debug
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.config.CoreConfigService
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

internal class ApiUrlBuilder(
    private val coreConfigService: CoreConfigService,
    private val metadataService: MetadataService,
    private val enableIntegrationTesting: Boolean,
    private val isDebug: Boolean
) {

    companion object {
        private const val API_VERSION = 1
        private const val CONFIG_API_VERSION = 2
    }

    private val baseUrls: SdkEndpointBehavior
        get() = coreConfigService.sdkEndpointBehavior

    private fun getConfigBaseUrl() = buildUrl(baseUrls.getConfig(getAppId()), CONFIG_API_VERSION, "config")
    private fun getOperatingSystemCode() = Build.VERSION.SDK_INT.toString() + ".0.0"

    private fun getCoreBaseUrl(): String = if (isDebugBuild()) {
        "${baseUrls.getDataDev(getAppId())}"
    } else {
        "${baseUrls.getData(getAppId())}"
    }

    private fun getAppVersion(): String = metadataService.getAppVersionName()

    private fun getAppId() = coreConfigService.sdkAppBehavior.appId

    private fun isDebugBuild(): Boolean {
        return isDebug && enableIntegrationTesting &&
            (Debug.isDebuggerConnected() || Debug.waitingForDebugger())
    }

    private fun buildUrl(config: String, configApiVersion: Int, path: String): String {
        return "$config/v$configApiVersion/$path"
    }

    fun getConfigUrl(): String {
        return "${getConfigBaseUrl()}?appId=${getAppId()}&osVersion=${getOperatingSystemCode()}" +
            "&appVersion=${getAppVersion()}&deviceId=${metadataService.getDeviceId()}"
    }

    fun getEmbraceUrlWithSuffix(suffix: String): String {
        InternalStaticEmbraceLogger.logDeveloper(
            "ApiUrlBuilder",
            "getEmbraceUrlWithSuffix - suffix: $suffix"
        )
        return "${getCoreBaseUrl()}/v$API_VERSION/log/$suffix"
    }
}
