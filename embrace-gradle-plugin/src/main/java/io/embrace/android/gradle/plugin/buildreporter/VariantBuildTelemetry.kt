package io.embrace.android.gradle.plugin.buildreporter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class VariantBuildTelemetry(
    @Json(name = "variant_name")
    @SerialName("variant_name")
    val variantName: String? = null,
    @Json(name = "app_id")
    @SerialName("app_id")
    val appId: String? = null,
    @Json(name = "build_id")
    @SerialName("build_id")
    val buildId: String? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
