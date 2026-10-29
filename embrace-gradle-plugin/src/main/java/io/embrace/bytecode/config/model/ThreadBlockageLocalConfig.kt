package io.embrace.bytecode.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadBlockageLocalConfig(
    @SerialName("capture_unity_thread")
    val captureUnityThread: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
