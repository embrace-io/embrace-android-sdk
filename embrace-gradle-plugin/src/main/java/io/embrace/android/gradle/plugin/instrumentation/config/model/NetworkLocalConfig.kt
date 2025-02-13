package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Represents the networking configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
data class NetworkLocalConfig(

    /**
     * The default capture limit for the specified domains.
     */
    @Json(name = "default_capture_limit")
    val defaultCaptureLimit: Int? = null,

    @Json(name = "domains")
    val domains: List<DomainLocalConfig>? = null,

    @Json(name = "capture_request_content_length")
    val captureRequestContentLength: Boolean? = null,

    @Json(name = "disabled_url_patterns")
    val disabledUrlPatterns: List<String>? = null,

    @Json(name = "enable_native_monitoring")
    val enableNativeMonitoring: Boolean? = null,

    @Json(name = "enable_network_span_forwarding")
    val enableNetworkSpanForwarding: Boolean? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
