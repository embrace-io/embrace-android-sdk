package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configuration of the SDK set by the Embrace API.
 */
internal data class RemoteConfig(

    /**
     * Used to determine whether or not the SDK should be activated for this device. The threshold
     * identifies the percentage of devices for which the SDK is enabled. A threshold of 100 means
     * that the SDK is enabled for all devices, whilst 0 means it is disabled for all devices.
     */
    @SerializedName("threshold")
    val threshold: Int? = null,

    /**
     * Used to shift the offset of devices for which the SDK is enabled/disabled.
     */
    @SerializedName("offset")
    val offset: Int? = null,

    /**
     * The time in milliseconds after which a particular event ID is considered 'late'.
     */
    @SerializedName("event_limits")
    val eventLimits: Map<String, Long>? = null,

    /**
     * The list of [io.embrace.android.embracesdk.MessageType] which are disabled.
     */
    @SerializedName("disabled_message_types")
    val disabledMessageTypes: Set<String>? = null,

    /**
     * List of regular expressions matching event names and log messages which should be disabled.
     */
    @SerializedName("disabled_event_and_log_patterns")
    val disabledEventAndLogPatterns: Set<String>? = null,

    /**
     * List of regular expressions of URLs which should not be logged.
     */
    @SerializedName("disabled_url_patterns")
    val disabledUrlPatterns: Set<String>? = null,

    /**
     * Rules that will allow the specification of network requests to be captured
     */
    @SerializedName("network_capture")
    val networkCaptureRules: Set<NetworkCaptureRuleRemoteConfig>? = null,

    /**
     * Settings relating to the user interface, such as the breadcrumb limits.
     */
    @SerializedName("ui")
    val uiConfig: UiRemoteConfig? = null,

    /**
     * Settings defining the capture limits for network calls.
     */
    @SerializedName("network")
    val networkConfig: NetworkRemoteConfig? = null,

    /**
     * Settings defining session control is enabled or not
     */
    @SerializedName("session_control")
    val sessionConfig: SessionRemoteConfig? = null,

    /**
     * Settings defining the log configuration.
     */
    @SerializedName("logs")
    val logConfig: LogRemoteConfig? = null,

    @SerializedName("anr")
    val anrConfig: AnrRemoteConfig? = null,

    @SerializedName("killswitch")
    val killSwitchConfig: KillSwitchRemoteConfig? = null,

    /**
     * Settings defining if internal exception capture is enabled or not
     */
    @SerializedName("internal_exception_capture_enabled")
    val internalExceptionCaptureEnabled: Boolean? = null,

    @SerializedName("pct_beta_features_enabled")
    val pctBetaFeaturesEnabled: Float? = null,

    @SerializedName("app_exit_info")
    val appExitInfoConfig: AppExitInfoConfig? = null,

    @SerializedName("background")
    val backgroundActivityConfig: BackgroundActivityRemoteConfig? = null,

    /**
     * The maximum number of properties that can be attached to a session
     */
    @SerializedName("max_session_properties")
    val maxSessionProperties: Int? = null,

    @SerializedName("spans")
    val spansConfig: SpansRemoteConfig? = null,

    @SerializedName("network_span_forwarding")
    val networkSpanForwardingRemoteConfig: NetworkSpanForwardingRemoteConfig? = null,

    /**
     * Web view vitals settings
     */
    @SerializedName("webview_vitals_beta")
    val webViewVitals: WebViewVitals? = null
)
