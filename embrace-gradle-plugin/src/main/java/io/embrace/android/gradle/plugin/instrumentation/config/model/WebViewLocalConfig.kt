package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class WebViewLocalConfig(
    @Json(name = "enable")
    val captureWebViews: Boolean? = null,

    @Json(name = "capture_query_params")
    val captureQueryParams: Boolean? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
