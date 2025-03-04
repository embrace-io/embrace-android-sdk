package io.embrace.android.gradle.integration.framework.smali

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SmaliFile(
    val className: String,
    val methods: List<SmaliMethod>,
)
