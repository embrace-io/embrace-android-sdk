package io.embrace.android.gradle.integration.utils

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NdkSymbols(
    val symbols: Map<String, Map<String, String>>? = null
)
