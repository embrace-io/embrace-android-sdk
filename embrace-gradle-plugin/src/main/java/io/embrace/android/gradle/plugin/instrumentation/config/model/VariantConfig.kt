package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.JsonClass
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
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
