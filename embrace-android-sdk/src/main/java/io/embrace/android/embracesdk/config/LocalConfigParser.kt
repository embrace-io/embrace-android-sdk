package io.embrace.android.embracesdk.config

import android.util.Base64
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

internal object LocalConfigParser {

    /**
     * Build info app id name.
     */
    public const val BUILD_INFO_APP_ID = "emb_app_id"

    /**
     * Build info sdk config id name.
     */
    private const val BUILD_INFO_SDK_CONFIG = "emb_sdk_config"

    /**
     * Build info ndk enabled.
     */
    public const val BUILD_INFO_NDK_ENABLED = "emb_ndk_enabled"

    /**
     * The default value for native crash capture enabling
     */
    public const val NDK_ENABLED_DEFAULT = false

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
        serializer: EmbraceSerializer
    ): LocalConfig {
        return try {
            val appId: String = customAppId ?: resources.getString(
                resources.getIdentifier(
                    BUILD_INFO_APP_ID,
                    "string",
                    packageName
                )
            )
            val ndkEnabledJsonId =
                resources.getIdentifier(BUILD_INFO_NDK_ENABLED, "string", packageName)
            val ndkEnabled = when {
                ndkEnabledJsonId != 0 -> java.lang.Boolean.parseBoolean(
                    resources.getString(
                        ndkEnabledJsonId
                    )
                )

                else -> NDK_ENABLED_DEFAULT
            } && !ApkToolsConfig.IS_NDK_DISABLED
            val sdkConfigJsonId =
                resources.getIdentifier(BUILD_INFO_SDK_CONFIG, "string", packageName)

            val sdkConfigJson: String? = when {
                sdkConfigJsonId != 0 -> {
                    val encodedConfig = resources.getString(sdkConfigJsonId)
                    String(Base64.decode(encodedConfig, Base64.DEFAULT))
                }

                else -> null
            }
            buildConfig(appId, ndkEnabled, sdkConfigJson, serializer)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to load local config from resources.", ex)
        }
    }

    fun buildConfig(
        appId: String?,
        ndkEnabled: Boolean,
        sdkConfigs: String?,
        serializer: EmbraceSerializer
    ): LocalConfig {
        require(!appId.isNullOrEmpty()) { "Embrace AppId cannot be null or empty." }

        val enabledStr = when {
            ndkEnabled -> "enabled"
            else -> "disabled"
        }
        InternalStaticEmbraceLogger.logInfo("Native crash capture is $enabledStr")
        var configs: SdkLocalConfig? = null
        if (!sdkConfigs.isNullOrEmpty()) {
            try {
                configs = serializer.fromJson(sdkConfigs, SdkLocalConfig::class.java)
            } catch (ex: Exception) {
                InternalStaticEmbraceLogger.logError(
                    "Failed to parse Embrace config from config json file.",
                    ex
                )
            }
        }
        if (configs == null) {
            configs = SdkLocalConfig()
        }
        return LocalConfig(appId, ndkEnabled, configs)
    }
}
