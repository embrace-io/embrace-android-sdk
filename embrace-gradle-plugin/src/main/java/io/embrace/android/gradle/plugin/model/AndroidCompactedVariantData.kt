package io.embrace.android.gradle.plugin.model

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.util.UuidUtils
import java.io.Serializable

/**
 * It represents all needed data from a Variant.
 */
data class AndroidCompactedVariantData(
    val name: String,
    val flavorName: String,
    val buildTypeName: String,
    val isBuildTypeDebuggable: Boolean,
    val versionName: String?,
    val versionCode: Int?,
    val productFlavors: List<String>,
    val sourceMapPath: String,
    val buildId: String = UuidUtils.generateEmbraceUuid()
) : Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        fun from(variant: Variant): AndroidCompactedVariantData {
            val buildtype = variant.buildType?.lowercase()
            val debuggable = buildtype?.contains("debug") ?: false
            
            // Extract version information from variant outputs
            val versionName = variant.outputs.firstOrNull()?.versionName?.orNull
            val versionCode = variant.outputs.firstOrNull()?.versionCode?.orNull
            
            return AndroidCompactedVariantData(
                variant.name,
                variant.flavorName ?: "",
                variant.buildType ?: "",
                debuggable,
                versionName,
                versionCode,
                fetchProductFlavors(variant),
                variant.name
            )
        }

        private fun fetchProductFlavors(variant: Variant) =
            variant.productFlavors.map {
                it.second
            }
    }
}
