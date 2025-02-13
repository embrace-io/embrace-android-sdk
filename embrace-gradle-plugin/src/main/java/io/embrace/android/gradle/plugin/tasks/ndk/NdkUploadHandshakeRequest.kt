package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class NdkUploadHandshakeRequest(
    @Json(name = "app")
    val appId: String,
    @Json(name = "token")
    val apiToken: String,
    @Json(name = "variant")
    val variant: String?,
    @Json(name = "archs")
    val archSymbols: Map<String, Map<String, String>>
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
