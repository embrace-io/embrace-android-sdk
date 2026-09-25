package io.embrace.android.gradle.network

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class NdkHandshakeRequestBody(
    val app: String,
    val token: String,
    val variant: String,
    val archs: Map<String, Map<String, String>>,
)
