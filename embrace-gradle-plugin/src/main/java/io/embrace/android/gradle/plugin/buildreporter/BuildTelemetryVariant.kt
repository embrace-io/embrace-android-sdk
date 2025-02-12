package io.embrace.android.gradle.plugin.buildreporter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class BuildTelemetryVariant(
    @Json(name = "vn") val variantName: String? = null,
    @Json(name = "aid") val appId: String? = null,
    @Json(name = "bid") val buildId: String? = null,
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
