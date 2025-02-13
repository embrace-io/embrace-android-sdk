package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class NdkUploadHandshakeResponse(
    @Json(name = "archs")
    val symbols: Map<String, List<String>>?
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
