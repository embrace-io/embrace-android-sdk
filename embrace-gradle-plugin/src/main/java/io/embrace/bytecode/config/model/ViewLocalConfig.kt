package io.embrace.bytecode.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ViewLocalConfig(

    @SerialName("enable_automatic_activity_capture")
    val enableAutomaticActivityCapture: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
