package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class SdkLocalConfig(
    /**
     * Service enablement config settings
     */
    @Json(name = "automatic_data_capture")
    public val automaticDataCaptureConfig: AutomaticDataCaptureLocalConfig? = null,

    /**
     * Taps
     */
    @Json(name = "taps")
    public val taps: TapsLocalConfig? = null,

    /**
     * Webview settings
     */
    @Json(name = "view_config")
    public val viewConfig: ViewLocalConfig? = null,

    /**
     * Webview settings
     */
    @Json(name = "webview")
    public val webViewConfig: WebViewLocalConfig? = null,

    /**
     * Whether beta features should be enabled or not
     */
    @Json(name = "beta_features_enabled")
    public val betaFeaturesEnabled: Boolean? = null,

    /**
     * Crash handler settings
     */
    @Json(name = "crash_handler")
    public val crashHandler: CrashHandlerLocalConfig? = null,

    /**
     * Compose settings
     */
    @Json(name = "compose")
    public val composeConfig: ComposeLocalConfig? = null,

    /**
     * Whether fcm PII data should be hidden or not
     */
    @Json(name = "capture_fcm_pii_data")
    public val captureFcmPiiData: Boolean? = null,

    /**
     * Networking moment settings
     */
    @Json(name = "networking")
    public val networking: NetworkLocalConfig? = null,

    /**
     * List of strings for sensitive keys that should be redacted when they are sent to the server.
     */
    @Json(name = "sensitive_keys_denylist")
    public val sensitiveKeysDenylist: List<String>? = null,

    @Json(name = "capture_public_key")
    public val capturePublicKey: String? = null,

    /**
     * ANR settings
     */
    @Json(name = "anr")
    public val anr: AnrLocalConfig? = null,

    /**
     * App settings
     */
    @Json(name = "app")
    public val app: AppLocalConfig? = null,

    /**
     * Background activity config settings
     */
    @Json(name = "background_activity")
    public val backgroundActivityConfig: BackgroundActivityLocalConfig? = null,

    /**
     * Base URL settings
     */
    @Json(name = "base_urls")
    public val baseUrls: BaseUrlLocalConfig? = null,

    /**
     * Startup moment settings
     */
    @Json(name = "startup_moment")
    public val startupMoment: StartupMomentLocalConfig? = null,

    /**
     * Session config settings
     */
    @Json(name = "session")
    public val sessionConfig: SessionLocalConfig? = null,

    /**
     * Whether signal handler detection should be enabled or not
     */
    @Json(name = "sig_handler_detection")
    public val sigHandlerDetection: Boolean? = null,

    /**
     * Background activity config settings
     */
    @Json(name = "app_exit_info")
    public val appExitInfoConfig: AppExitInfoLocalConfig? = null,

    @Json(name = "app_framework")
    public val appFramework: String? = null
)
