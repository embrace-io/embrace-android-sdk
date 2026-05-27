package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration of the SDK set by the Embrace API.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class RemoteConfig(

    /**
     * Used to determine whether or not the SDK should be activated for this device. The threshold
     * identifies the percentage of devices for which the SDK is enabled. A threshold of 100 means
     * that the SDK is enabled for all devices, whilst 0 means it is disabled for all devices.
     */
    @SerialName("threshold")
    @Json(name = "threshold")
    val threshold: Int? = null,

    /**
     * List of regular expressions matching event names and log messages which should be disabled.
     */
    @SerialName("disabled_event_and_log_patterns")
    @Json(name = "disabled_event_and_log_patterns")
    val disabledEventAndLogPatterns: Set<String>? = null,

    /**
     * List of regular expressions of URLs which should not be logged.
     */
    @SerialName("disabled_url_patterns")
    @Json(name = "disabled_url_patterns")
    val disabledUrlPatterns: Set<String>? = null,

    /**
     * Rules that will allow the specification of network requests to be captured
     */
    @SerialName("network_capture")
    @Json(name = "network_capture")
    val networkCaptureRules: Set<NetworkCaptureRuleRemoteConfig>? = null,

    /**
     * Settings relating to the user interface, such as the breadcrumb limits.
     */
    @SerialName("ui")
    @Json(name = "ui")
    val uiConfig: UiRemoteConfig? = null,

    /**
     * Settings defining the capture limits for network calls.
     */
    @SerialName("network")
    @Json(name = "network")
    val networkConfig: NetworkRemoteConfig? = null,

    /**
     * Settings defining session control is enabled or not
     */
    @SerialName("session_control")
    @Json(name = "session_control")
    val sessionConfig: SessionRemoteConfig? = null,

    /**
     * Settings defining the log configuration.
     */
    @SerialName("logs")
    @Json(name = "logs")
    val logConfig: LogRemoteConfig? = null,

    @SerialName("anr")
    @Json(name = "anr")
    val threadBlockageRemoteConfig: ThreadBlockageRemoteConfig? = null,

    @SerialName("data")
    @Json(name = "data")
    val dataConfig: DataRemoteConfig? = null,

    @SerialName("killswitch")
    @Json(name = "killswitch")
    val killSwitchConfig: KillSwitchRemoteConfig? = null,

    /**
     * Settings defining if internal exception capture is enabled or not
     */
    @SerialName("internal_exception_capture_enabled")
    @Json(name = "internal_exception_capture_enabled")
    val internalExceptionCaptureEnabled: Boolean? = null,

    @SerialName("app_exit_info")
    @Json(name = "app_exit_info")
    val appExitInfoConfig: AppExitInfoConfig? = null,

    @SerialName("background")
    @Json(name = "background")
    val backgroundActivityConfig: BackgroundActivityRemoteConfig? = null,

    /**
     * The maximum number of properties that can be attached to a session
     */
    @SerialName("max_session_properties")
    @Json(name = "max_session_properties")
    val maxUserSessionProperties: Int? = null,

    @SerialName("network_span_forwarding")
    @Json(name = "network_span_forwarding")
    val networkSpanForwardingRemoteConfig: NetworkSpanForwardingRemoteConfig? = null,

    @SerialName("ui_load_instrumentation_enabled_v2")
    @Json(name = "ui_load_instrumentation_enabled_v2")
    val uiLoadInstrumentationEnabled: Boolean? = null,

    @SerialName("otel_kotlin_sdk")
    @Json(name = "otel_kotlin_sdk")
    val otelKotlinSdkConfig: OtelKotlinSdkConfig? = null,

    @SerialName("pct_state_enabled_v2")
    @Json(name = "pct_state_enabled_v2")
    val pctStateCaptureEnabledV2: Float? = null,

    /**
     * Percentage of devices for which the NetworkCallback-based NetworkConnectivityService implementation is enabled.
     */
    @SerialName("pct_network_callback_connectivity_service_enabled")
    @Json(name = "pct_network_callback_connectivity_service_enabled")
    val pctNetworkCallbackConnectivityServiceEnabled: Float? = null,

    @SerialName("pct_screen_tracking_enabled")
    @Json(name = "pct_screen_tracking_enabled")
    val pctNavigationStateCaptureEnabled: Float? = null,

    @SerialName("user_session")
    @Json(name = "user_session")
    val userSession: UserSessionRemoteConfig? = null,
)
