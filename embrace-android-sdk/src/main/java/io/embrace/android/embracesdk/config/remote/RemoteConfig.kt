package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration of the SDK set by the Embrace API.
 */
@JsonClass(generateAdapter = true)
internal data class RemoteConfig(

    /**
     * Used to determine whether or not the SDK should be activated for this device. The threshold
     * identifies the percentage of devices for which the SDK is enabled. A threshold of 100 means
     * that the SDK is enabled for all devices, whilst 0 means it is disabled for all devices.
     */
    @Json(name = "threshold")
    val threshold: Int? = null,

    /**
     * Used to shift the offset of devices for which the SDK is enabled/disabled.
     */
    @Json(name = "offset")
    val offset: Int? = null,

    /**
     * The time in milliseconds after which a particular event ID is considered 'late'.
     */
    @Json(name = "event_limits")
    val eventLimits: Map<String, Long>? = null,

    /**
     * List of regular expressions matching event names and log messages which should be disabled.
     */
    @Json(name = "disabled_event_and_log_patterns")
    val disabledEventAndLogPatterns: Set<String>? = null,

    /**
     * List of regular expressions of URLs which should not be logged.
     */
    @Json(name = "disabled_url_patterns")
    val disabledUrlPatterns: Set<String>? = null,

    /**
     * Rules that will allow the specification of network requests to be captured
     */
    @Json(name = "network_capture")
    val networkCaptureRules: Set<NetworkCaptureRuleRemoteConfig>? = null,

    /**
     * Settings relating to the user interface, such as the breadcrumb limits.
     */
    @Json(name = "ui")
    val uiConfig: UiRemoteConfig? = null,

    /**
     * Settings defining the capture limits for network calls.
     */
    @Json(name = "network")
    val networkConfig: NetworkRemoteConfig? = null,

    /**
     * Settings defining session control is enabled or not
     */
    @Json(name = "session_control")
    val sessionConfig: SessionRemoteConfig? = null,

    /**
     * Settings defining the log configuration.
     */
    @Json(name = "logs")
    val logConfig: LogRemoteConfig? = null,

    @Json(name = "anr")
    val anrConfig: AnrRemoteConfig? = null,

    @Json(name = "data")
    val dataConfig: DataRemoteConfig? = null,

    @Json(name = "killswitch")
    val killSwitchConfig: KillSwitchRemoteConfig? = null,

    /**
     * Settings defining if internal exception capture is enabled or not
     */
    @Json(name = "internal_exception_capture_enabled")
    val internalExceptionCaptureEnabled: Boolean? = null,

    @Json(name = "pct_beta_features_enabled")
    val pctBetaFeaturesEnabled: Float? = null,

    @Json(name = "app_exit_info")
    val appExitInfoConfig: AppExitInfoConfig? = null,

    @Json(name = "background")
    val backgroundActivityConfig: BackgroundActivityRemoteConfig? = null,

    /**
     * The maximum number of properties that can be attached to a session
     */
    @Json(name = "max_session_properties")
    val maxSessionProperties: Int? = null,

    @Json(name = "network_span_forwarding")
    val networkSpanForwardingRemoteConfig: NetworkSpanForwardingRemoteConfig? = null,

    /**
     * Web view vitals settings
     */
    @Json(name = "webview_vitals_beta")
    val webViewVitals: WebViewVitals? = null
)
