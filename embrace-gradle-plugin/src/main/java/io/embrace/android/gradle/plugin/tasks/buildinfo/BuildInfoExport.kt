package io.embrace.android.gradle.plugin.tasks.buildinfo

import kotlinx.serialization.Serializable

@Serializable
data class BuildInfoExport(
    val buildId: String,
    val appId: String,
    val variantName: String,
)
