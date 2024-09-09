package io.embrace.android.embracesdk.internal.config

import android.content.res.Resources.NotFoundException
import android.util.Base64
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer

internal object LocalConfigParser {

    /**
     * Build info app id name.
     */
    private const val BUILD_INFO_APP_ID: String = "emb_app_id"

    /**
     * Build info sdk config id name.
     */
    private const val BUILD_INFO_SDK_CONFIG = "emb_sdk_config"

    /**
     * Build info ndk enabled.
     */
    private const val BUILD_INFO_NDK_ENABLED: String = "emb_ndk_enabled"

    /**
     * The default value for native crash capture enabling
     */
    const val NDK_ENABLED_DEFAULT: Boolean = false

    /**
     * Loads the build information from resources provided by the config file packaged within the application by Gradle at
     * build-time.
     *
     * @return the local configuration
     */
    @JvmStatic
    fun fromResources(
        resources: AndroidResourcesService,
        packageName: String,
        customAppId: String?,
        serializer: PlatformSerializer,
        openTelemetryCfg: OpenTelemetryConfiguration,
        logger: EmbLogger
    ): LocalConfig {
        return try {
            Systrace.startSynchronous("cfg-resolve-resources")
            val appId = resolveAppId(customAppId, resources, packageName)
            val ndkEnabledJsonId =
                resources.getIdentifier(BUILD_INFO_NDK_ENABLED, "string", packageName)
            val ndkEnabled = when {
                ndkEnabledJsonId != 0 -> java.lang.Boolean.parseBoolean(
                    resources.getString(
                        ndkEnabledJsonId
                    )
                )

                else -> NDK_ENABLED_DEFAULT
            }
            val sdkConfigJsonId =
                resources.getIdentifier(BUILD_INFO_SDK_CONFIG, "string", packageName)
            Systrace.endSynchronous()

            val sdkConfigJson: String? = when {
                sdkConfigJsonId != 0 -> {
                    val encodedConfig = resources.getString(sdkConfigJsonId)
                    Systrace.traceSynchronous("base64-decode") {
                        String(Base64.decode(encodedConfig, Base64.DEFAULT))
                    }
                }
                else -> null
            }
            Systrace.traceSynchronous("build-config") {
                buildConfig(appId, ndkEnabled, sdkConfigJson, serializer, openTelemetryCfg, logger)
            }
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to load local config from resources.", ex)
        }
    }

    private fun resolveAppId(
        customAppId: String?,
        resources: AndroidResourcesService,
        packageName: String
    ): String? {
        return try {
            customAppId ?: resources.getString(
                resources.getIdentifier(
                    BUILD_INFO_APP_ID,
                    "string",
                    packageName
                )
            )
        } catch (exc: NotFoundException) {
            null
        }
    }

    fun buildConfig(
        appId: String?,
        ndkEnabled: Boolean,
        sdkConfigs: String?,
        serializer: PlatformSerializer,
        openTelemetryCfg: OpenTelemetryConfiguration,
        logger: EmbLogger
    ): LocalConfig {
        require(!appId.isNullOrEmpty() || openTelemetryCfg.hasConfiguredOtelExporters()) {
            "No appId supplied in embrace-config.json. This is required if you want to " +
                "send data to Embrace, unless you configure an OTel exporter and add" +
                " embrace.disableMappingFileUpload=true to gradle.properties."
        }

        var configs: SdkLocalConfig? = null
        if (!sdkConfigs.isNullOrEmpty()) {
            Systrace.traceSynchronous("deserialize-sdk-config") {
                try {
                    configs = serializer.fromJson(sdkConfigs, SdkLocalConfig::class.java)
                } catch (ex: Exception) {
                    logger.trackInternalError(InternalErrorType.CONFIG_DESERIALIZATION_FAIL, ex)
                }
            }
        }
        return LocalConfig(appId, ndkEnabled, configs ?: SdkLocalConfig())
    }
}
