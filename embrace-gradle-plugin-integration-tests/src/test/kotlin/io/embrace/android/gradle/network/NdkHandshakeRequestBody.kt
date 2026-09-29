package io.embrace.android.gradle.network

import kotlinx.serialization.Serializable

@Serializable
data class NdkHandshakeRequestBody(
    val app: String,
    val token: String,
    val variant: String,
    val archs: Map<String, Map<String, String>>,
)
