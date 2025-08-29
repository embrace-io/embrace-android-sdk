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
            
            // AGP 8.x compatibility: variant.outputs is not accessible in newer AGP versions
            // Version information will be extracted later from the Android extension in TaskRegistrar
            // This approach is more reliable and follows AGP 8.x best practices
            val versionName: String? = null
            val versionCode: Int? = null
            
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
