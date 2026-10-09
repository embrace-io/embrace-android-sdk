package io.embrace.android.gradle.plugin.instrumentation.config.model

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * This data class holds all configuration from Embrace and Android that is dependent on the
 * variant being built.
 */
@Serializable
data class VariantConfig(
    val variantName: String,
    val buildId: String? = null,
    val buildType: String? = null,
    val buildFlavor: String? = null,
    val embraceConfig: EmbraceVariantConfig? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        /**
         * It builds a full configuration for a variant.
         */
        fun from(
            embraceVariantConfig: EmbraceVariantConfig?,
            androidVariantConfig: AndroidCompactedVariantData,
            buildId: String? = null,
        ) =
            VariantConfig(
                embraceConfig = embraceVariantConfig,
                variantName = androidVariantConfig.name,
                buildType = androidVariantConfig.buildTypeName,
                buildFlavor = androidVariantConfig.flavorName,
                buildId = buildId,
            )
    }
}

/**
 * This data class holds all embrace configuration that is dependent on the variant being built.
 */
@Serializable
data class EmbraceVariantConfig(

    @SerialName("app_id")
    val appId: String?,

    @SerialName("api_token")
    val apiToken: String?,

    @SerialName("ndk_enabled")
    val ndkEnabled: Boolean?,

    @SerialName("sdk_config")
    val sdkConfig: SdkLocalConfig?,

    @SerialName("unity")
    val unityConfig: UnityConfig?,

    /** The whole config file as minified JSON, used to instrument generated SDK local config. */
    @Transient
    val json: String? = null,

) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
