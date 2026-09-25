package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class NdkUploadHandshakeResponse(
    @Json(name = "archs")
    @SerialName("archs")
    val symbols: Map<String, List<String>>?,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
