package io.embrace.android.gradle.plugin.tasks.buildinfo

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class BuildInfoExport(
    val buildId: String,
    val appId: String,
    val variantName: String,
)
