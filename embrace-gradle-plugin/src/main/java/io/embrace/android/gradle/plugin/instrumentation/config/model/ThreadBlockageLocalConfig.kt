package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class ThreadBlockageLocalConfig(
    @Json(name = "capture_unity_thread")
    @SerialName("capture_unity_thread")
    val captureUnityThread: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
