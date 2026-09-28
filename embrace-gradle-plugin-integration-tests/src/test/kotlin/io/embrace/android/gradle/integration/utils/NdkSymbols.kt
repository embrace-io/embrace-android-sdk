package io.embrace.android.gradle.integration.utils

import kotlinx.serialization.Serializable

@Serializable
data class NdkSymbols(
    val symbols: Map<String, Map<String, String>>? = null,
)
