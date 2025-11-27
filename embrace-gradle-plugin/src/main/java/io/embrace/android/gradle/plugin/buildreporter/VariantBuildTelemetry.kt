package io.embrace.android.gradle.plugin.buildreporter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class VariantBuildTelemetry(
    @Json(name = "variant_name") val variantName: String? = null,
    @Json(name = "app_id") val appId: String? = null,
    @Json(name = "build_id") val buildId: String? = null,
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
