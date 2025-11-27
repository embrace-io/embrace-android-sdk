package io.embrace.android.gradle.plugin.buildreporter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class BuildTelemetryRequest(
    @Json(name = "id") val buildTelemetryId: String,
    @Json(name = "variant_build_telemetry") val variantBuildTelemetry: List<VariantBuildTelemetry>? = null,
    @Json(name = "embrace_plugin_version") val embracePluginVersion: String? = null,
    @Json(name = "gradle_version") val gradleVersion: String? = null,
    @Json(name = "agp_version") val agpVersion: String? = null,
    @Json(name = "build_cache_enabled") val isBuildCacheEnabled: Boolean? = null,
    @Json(name = "config_cache_enabled") val isConfigCacheEnabled: Boolean? = null,
    @Json(name = "gradle_parallel_execution_enabled") val isGradleParallelExecutionEnabled: Boolean? = null,
    @Json(name = "isolated_projects_enabled") val isIsolatedProjectsEnabled: Boolean? = null,
    @Json(name = "jvm_args") val jvmArgs: String? = null,
    @Json(name = "os") val operatingSystem: String? = null,
    @Json(name = "jdk_version") val jdkVersion: String? = null,
    @Json(name = "unity_edm_enabled") val isEdmEnabled: Boolean? = null,
    @Json(name = "unity_edm_version") val edmVersion: String? = null,
    @Json(name = "kgp_version") val kotlinVersion: String? = null,
    @Json(name = "kotlin_jvm_target") val kotlinJvmTarget: String? = null,
    @Json(name = "source_compatibility") val sourceCompatibility: String? = null,
    @Json(name = "min_sdk") val minSdk: Int? = null,
    @Json(name = "compile_sdk") val compileSdk: Int? = null,
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
