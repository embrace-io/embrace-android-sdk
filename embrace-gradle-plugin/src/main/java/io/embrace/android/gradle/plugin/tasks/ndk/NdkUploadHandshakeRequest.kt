package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class NdkUploadHandshakeRequest(
    @Json(name = "app")
    @SerialName("app")
    val appId: String,
    @Json(name = "token")
    @SerialName("token")
    val apiToken: String,
    @Json(name = "variant")
    @SerialName("variant")
    val variant: String?,
    @Json(name = "archs")
    @SerialName("archs")
    val archSymbols: Map<String, Map<String, String>>,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
