package io.embrace.android.gradle.plugin.buildreporter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BuildTelemetryRequest(
    @SerialName("id")
    val buildTelemetryId: String,
    @SerialName("variant_build_telemetry")
    val variantBuildTelemetry: List<VariantBuildTelemetry>? = null,
    @SerialName("embrace_plugin_version")
    val embracePluginVersion: String? = null,
    @SerialName("gradle_version")
    val gradleVersion: String? = null,
    @SerialName("agp_version")
    val agpVersion: String? = null,
    @SerialName("build_cache_enabled")
    val isBuildCacheEnabled: Boolean? = null,
    @SerialName("config_cache_enabled")
    val isConfigCacheEnabled: Boolean? = null,
    @SerialName("gradle_parallel_execution_enabled")
    val isGradleParallelExecutionEnabled: Boolean? = null,
    @SerialName("isolated_projects_enabled")
    val isIsolatedProjectsEnabled: Boolean? = null,
    @SerialName("jvm_args")
    val jvmArgs: String? = null,
    @SerialName("os")
    val operatingSystem: String? = null,
    @SerialName("jdk_version")
    val jdkVersion: String? = null,
    @SerialName("unity_edm_enabled")
    val isEdmEnabled: Boolean? = null,
    @SerialName("unity_edm_version")
    val edmVersion: String? = null,
    @SerialName("kgp_version")
    val kotlinVersion: String? = null,
    @SerialName("kotlin_jvm_target")
    val kotlinJvmTarget: String? = null,
    @SerialName("source_compatibility")
    val sourceCompatibility: String? = null,
    @SerialName("min_sdk")
    val minSdk: Int? = null,
    @SerialName("compile_sdk")
    val compileSdk: Int? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
