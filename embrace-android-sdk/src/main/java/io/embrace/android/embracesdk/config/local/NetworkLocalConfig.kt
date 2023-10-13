package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents the networking configuration element specified in the Embrace config file.
 */
internal class NetworkLocalConfig(
    @SerializedName("trace_id_header")
    val traceIdHeader: String? = null,

    /**
     * The default capture limit for the specified domains.
     */
    @SerializedName("default_capture_limit")
    val defaultCaptureLimit: Int? = null,

    @SerializedName("domains")
    val domains: List<DomainLocalConfig>? = null,

    @SerializedName("capture_request_content_length")
    val captureRequestContentLength: Boolean? = null,

    @SerializedName("disabled_url_patterns")
    val disabledUrlPatterns: List<String>? = null,

    @SerializedName("enable_native_monitoring")
    val enableNativeMonitoring: Boolean? = null
)
