package io.embrace.android.gradle.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NdkHandshakeRequestBody(
    val app: String,
    val token: String,
    val variant: String,
    val archs: Map<String, Map<String, String>>
)
