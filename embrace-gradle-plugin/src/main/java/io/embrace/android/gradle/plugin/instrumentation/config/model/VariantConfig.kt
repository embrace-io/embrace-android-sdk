package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import java.io.Serializable

/**
 * This data class holds all configuration from Embrace and Android that is dependent on the
 * variant being built.
 */
@JsonClass(generateAdapter = true)
data class VariantConfig(
    val variantName: String,
    val variantVersion: String? = null,
    val buildId: String? = null,
    val buildType: String? = null,
    val buildFlavor: String? = null,
    val embraceConfig: EmbraceVariantConfig? = null
) : Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        /**
         * It builds a full configuration for a variant.
         */
        fun from(
            embraceVariantConfig: EmbraceVariantConfig?,
            androidVariantConfig: AndroidCompactedVariantData
        ) =
            VariantConfig(
                embraceConfig = embraceVariantConfig,
                variantName = androidVariantConfig.name,
                variantVersion = androidVariantConfig.versionName,
                buildType = androidVariantConfig.buildTypeName,
                buildFlavor = androidVariantConfig.flavorName,
                buildId = androidVariantConfig.buildId,
            )
    }
}

/**
 * This data class holds all embrace configuration that is dependent on the variant being built.
 */
@JsonClass(generateAdapter = true)
data class EmbraceVariantConfig(

    @Json(name = "app_id")
    val appId: String?,

    @Json(name = "api_token")
    val apiToken: String?,

    @Json(name = "ndk_enabled")
    val ndkEnabled: Boolean?,

    @Json(name = "sdk_config")
    val sdkConfig: SdkLocalConfig?,

    @Json(name = "unity")
    val unityConfig: UnityConfig?,

    val configStr: String? = null,

) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
