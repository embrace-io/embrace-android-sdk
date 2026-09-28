package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the networking configuration element specified in the Embrace config file.
 */
@Serializable
data class NetworkLocalConfig(

    /**
     * The default capture limit for the specified domains.
     */
    @SerialName("default_capture_limit")
    val defaultCaptureLimit: Int? = null,

    @SerialName("domains")
    val domains: List<DomainLocalConfig>? = null,

    @SerialName("capture_request_content_length")
    val captureRequestContentLength: Boolean? = null,

    @SerialName("capture_okhttp_response_body_size")
    val captureOkHttpResponseBodySize: Boolean? = null,

    @SerialName("disabled_url_patterns")
    val disabledUrlPatterns: List<String>? = null,

    @SerialName("enable_native_monitoring")
    val enableNativeMonitoring: Boolean? = null,

    @SerialName("enable_huc_lite_instrumentation")
    val enableHucLiteInstrumentation: Boolean? = null,

    @SerialName("enable_network_span_forwarding")
    val enableNetworkSpanForwarding: Boolean? = null,

    @SerialName("enable_traceparent_injection")
    val enableTraceparentInjection: Boolean? = null,

    @SerialName("traceparent_only_allow_domains")
    val traceparentOnlyAllowDomains: List<String>? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
