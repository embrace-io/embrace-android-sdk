package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class SdkLocalConfig(
    /**
     * Service enablement config settings
     */
    @SerializedName("automatic_data_capture")
    val automaticDataCaptureConfig: AutomaticDataCaptureLocalConfig? = null,

    /**
     * Taps
     */
    @SerializedName("taps")
    val taps: TapsLocalConfig? = null,

    /**
     * Webview settings
     */
    @SerializedName("view_config")
    val viewConfig: ViewLocalConfig? = null,

    /**
     * Webview settings
     */
    @SerializedName("webview")
    val webViewConfig: WebViewLocalConfig? = null,

    /**
     * Whether integration mode should be enabled or not
     */
    @SerializedName("integration_mode")
    val integrationModeEnabled: Boolean? = null,

    /**
     * Whether beta features should be enabled or not
     */
    @SerializedName("beta_features_enabled")
    val betaFeaturesEnabled: Boolean? = null,

    /**
     * Crash handler settings
     */
    @SerializedName("crash_handler")
    val crashHandler: CrashHandlerLocalConfig? = null,

    /**
     * Compose settings
     */
    @SerializedName("compose")
    val composeConfig: ComposeLocalConfig? = null,

    /**
     * Whether fcm PII data should be hidden or not
     */
    @SerializedName("capture_fcm_pii_data")
    val captureFcmPiiData: Boolean? = null,

    /**
     * Networking moment settings
     */
    @SerializedName("networking")
    val networking: NetworkLocalConfig? = null,

    @SerializedName("capture_public_key")
    val capturePublicKey: String? = null,

    /**
     * ANR settings
     */
    @SerializedName("anr")
    val anr: AnrLocalConfig? = null,

    /**
     * App settings
     */
    @SerializedName("app")
    val app: AppLocalConfig? = null,

    /**
     * Background activity config settings
     */
    @SerializedName("background_activity")
    val backgroundActivityConfig: BackgroundActivityLocalConfig? = null,

    /**
     * Base URL settings
     */
    @SerializedName("base_urls")
    val baseUrls: BaseUrlLocalConfig? = null,

    /**
     * Startup moment settings
     */
    @SerializedName("startup_moment")
    val startupMoment: StartupMomentLocalConfig? = null,

    /**
     * Session config settings
     */
    @SerializedName("session")
    val sessionConfig: SessionLocalConfig? = null,

    /**
     * Whether signal handler detection should be enabled or not
     */
    @SerializedName("sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,

    /**
     * Background activity config settings
     */
    @SerializedName("app_exit_info")
    val appExitInfoConfig: AppExitInfoLocalConfig? = null,
)
