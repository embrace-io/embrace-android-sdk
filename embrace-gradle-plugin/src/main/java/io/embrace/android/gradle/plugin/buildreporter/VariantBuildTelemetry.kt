package io.embrace.android.gradle.plugin.buildreporter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VariantBuildTelemetry(
    @SerialName("variant_name")
    val variantName: String? = null,
    @SerialName("app_id")
    val appId: String? = null,
    @SerialName("build_id")
    val buildId: String? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
