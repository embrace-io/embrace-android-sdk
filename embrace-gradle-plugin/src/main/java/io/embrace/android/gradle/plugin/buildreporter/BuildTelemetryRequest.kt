package io.embrace.android.gradle.plugin.buildreporter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class BuildTelemetryRequest(
    @Json(name = "id") val metadataRequestId: String,
    @Json(name = "v") val variantMetadata: List<BuildTelemetryVariant>? = null,
    @Json(name = "sv") val pluginVersion: String? = null,
    @Json(name = "gv") val gradleVersion: String? = null,
    @Json(name = "agpv") val agpVersion: String? = null,
    @Json(name = "bc") val isBuildCacheEnabled: Boolean? = null,
    @Json(name = "cc") val isConfigCacheEnabled: Boolean? = null,
    @Json(name = "gpe") val isGradleParallelExecutionEnabled: Boolean? = null,
    @Json(name = "jvma") val jvmArgs: String? = null,
    @Json(name = "os") val operatingSystem: String? = null,
    @Json(name = "jre") val jreVersion: String? = null,
    @Json(name = "jdk") val jdkVersion: String? = null,
    @Json(name = "edm") val isEdmEnabled: Boolean? = null,
    @Json(name = "edmv") val edmVersion: String? = null,
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
