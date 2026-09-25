package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class WebViewLocalConfig(
    @Json(name = "enable")
    @SerialName("enable")
    val captureWebViews: Boolean? = null,

    @Json(name = "capture_query_params")
    @SerialName("capture_query_params")
    val captureQueryParams: Boolean? = null,

    @Json(name = "fragment_capture")
    @SerialName("fragment_capture")
    val fragmentCapture: FragmentCapture? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }

    /**
     * Mirrors the SDK's own WebViewFragmentCapture enum, which is instrumented by constant name.
     * Declaring the states here rather than taking a String means an unsupported value fails the
     * build instead of silently falling back to the default.
     */
    enum class FragmentCapture {

        @Json(name = "keep")
        @SerialName("keep")
        KEEP,

        @Json(name = "redact")
        @SerialName("redact")
        REDACT,

        @Json(name = "remove")
        @SerialName("remove")
        REMOVE,
    }
}
