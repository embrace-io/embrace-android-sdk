package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * This data class holds all configuration from Embrace and Android that is dependent on the
 * variant being built.
 */
@JsonClass(generateAdapter = true)
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
@JsonClass(generateAdapter = true)
@Serializable
data class EmbraceVariantConfig(

    @Json(name = "app_id")
    @SerialName("app_id")
    val appId: String?,

    @Json(name = "api_token")
    @SerialName("api_token")
    val apiToken: String?,

    @Json(name = "ndk_enabled")
    @SerialName("ndk_enabled")
    val ndkEnabled: Boolean?,

    @Json(name = "sdk_config")
    @SerialName("sdk_config")
    val sdkConfig: SdkLocalConfig?,

    @Json(name = "unity")
    @SerialName("unity")
    val unityConfig: UnityConfig?,

) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
