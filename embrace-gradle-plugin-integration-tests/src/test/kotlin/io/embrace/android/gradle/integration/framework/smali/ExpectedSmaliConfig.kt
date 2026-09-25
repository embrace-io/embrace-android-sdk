package io.embrace.android.gradle.integration.framework.smali

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class ExpectedSmaliConfig(
    val values: List<SmaliFile>,
)
