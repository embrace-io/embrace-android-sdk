package io.embrace.android.gradle.plugin.model

import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantOutputConfiguration
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
    val versionCode: String?,
    val packageName: String?,
    val productFlavors: List<String>,
    val sourceMapPath: String,
    val buildId: String = UuidUtils.generateEmbraceUuid(),
) : Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        fun from(variant: Variant): AndroidCompactedVariantData {
            val appVariant = variant as ApplicationVariant
            val mainOutput = appVariant.outputs.single {
                it.outputType == VariantOutputConfiguration.OutputType.SINGLE
            }
            val buildType = variant.buildType?.lowercase()
            val debuggable = buildType?.contains("debug") ?: false
            return AndroidCompactedVariantData(
                variant.name,
                variant.flavorName ?: "",
                variant.buildType ?: "",
                debuggable,
                mainOutput.versionName.orNull ?: "",
                mainOutput.versionCode.orNull?.toString() ?: "",
                appVariant.applicationId.get(),
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
