package io.embrace.android.gradle.plugin.tasks.buildinfo

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BuildInfoExport(
    val buildId: String,
    val appId: String,
    val variantName: String,
)
