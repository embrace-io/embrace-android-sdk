package io.embrace.android.gradle.plugin.tasks.ndk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NdkUploadHandshakeResponse(
    @SerialName("archs")
    val symbols: Map<String, List<String>>?,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
