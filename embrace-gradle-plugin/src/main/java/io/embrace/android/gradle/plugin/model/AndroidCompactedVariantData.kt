package io.embrace.android.gradle.plugin.model

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import java.io.Serializable

/**
 * It represents all needed data from a Variant.
 */
data class AndroidCompactedVariantData(
    val name: String,
    val flavorName: String,
    val buildTypeName: String,
    val isBuildTypeDebuggable: Boolean,
    val productFlavors: List<String>,
    val sourceMapPath: String,
) : Serializable {

    fun toVariantConfig(embraceConfig: EmbraceVariantConfig?): VariantConfig = VariantConfig(
        variantName = name,
        buildType = buildTypeName,
        buildFlavor = flavorName,
        embraceConfig = embraceConfig,
    )

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        fun from(variant: Variant): AndroidCompactedVariantData {
            val buildtype = variant.buildType?.lowercase()
            val debuggable = buildtype?.contains("debug") ?: false
            return AndroidCompactedVariantData(
                variant.name,
                variant.flavorName ?: "",
                variant.buildType ?: "",
                debuggable,
                fetchProductFlavors(variant),
                variant.name,
            )
        }

        private fun fetchProductFlavors(variant: Variant) =
            variant.productFlavors.map {
                it.second
            }
    }
}
