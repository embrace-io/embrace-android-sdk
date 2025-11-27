package io.embrace.android.gradle.integration.framework.smali

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExpectedSmaliConfig(
    val values: List<SmaliFile>,
)
