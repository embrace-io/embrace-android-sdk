package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Unity-specific configuration.
 */
@JsonClass(generateAdapter = true)
data class UnityConfig(
    @Json(name = "symbols_archive_name")
    val symbolsArchiveName: String?
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
