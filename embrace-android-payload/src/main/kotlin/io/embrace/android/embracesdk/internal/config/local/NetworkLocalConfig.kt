package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the networking configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
public class NetworkLocalConfig(
    @Json(name = "trace_id_header")
    public val traceIdHeader: String? = null,

    /**
     * The default capture limit for the specified domains.
     */
    @Json(name = "default_capture_limit")
    public val defaultCaptureLimit: Int? = null,

    @Json(name = "domains")
    public val domains: List<DomainLocalConfig>? = null,

    @Json(name = "capture_request_content_length")
    public val captureRequestContentLength: Boolean? = null,

    @Json(name = "disabled_url_patterns")
    public val disabledUrlPatterns: List<String>? = null,

    @Json(name = "enable_native_monitoring")
    public val enableNativeMonitoring: Boolean? = null
)
