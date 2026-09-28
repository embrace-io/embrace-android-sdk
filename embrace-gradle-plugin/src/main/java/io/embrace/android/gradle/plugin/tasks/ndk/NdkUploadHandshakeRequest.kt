package io.embrace.android.gradle.plugin.tasks.ndk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NdkUploadHandshakeRequest(
    @SerialName("app")
    val appId: String,
    @SerialName("token")
    val apiToken: String,
    @SerialName("variant")
    val variant: String?,
    @SerialName("archs")
    val archSymbols: Map<String, Map<String, String>>,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
