package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Represents the crash handler element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
data class CrashHandlerLocalConfig(
    @Json(name = "enabled")
    val enabled: Boolean? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
