package io.embrace.android.gradle.integration.framework.smali

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class SmaliFile(
    val className: String,
    val methods: List<SmaliMethod>,
)
