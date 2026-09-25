package io.embrace.android.gradle.integration.utils

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class NdkSymbols(
    val symbols: Map<String, Map<String, String>>? = null,
)
