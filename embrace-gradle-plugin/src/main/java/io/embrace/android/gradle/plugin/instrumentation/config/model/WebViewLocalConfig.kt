package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebViewLocalConfig(
    @SerialName("enable")
    val captureWebViews: Boolean? = null,

    @SerialName("capture_query_params")
    val captureQueryParams: Boolean? = null,

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

        @SerialName("keep")
        KEEP,

        @SerialName("redact")
        REDACT,

        @SerialName("remove")
        REMOVE,
    }
}
