package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class SdkLocalConfig(
    /**
     * Service enablement config settings
     */
    @Json(name = "automatic_data_capture")
    val automaticDataCaptureConfig: AutomaticDataCaptureLocalConfig? = null,

    /**
     * Taps
     */
    @Json(name = "taps")
    val taps: TapsLocalConfig? = null,

    /**
     * OpenTelemetry config settings
     */
    @Json(name = "otel")
    val otel: OpenTelemetryLocalConfig? = null,

    /**
     * View settings
     */
    @Json(name = "view_config")
    val viewConfig: ViewLocalConfig? = null,

    /**
     * Webview settings
     */
    @Json(name = "webview")
    val webViewConfig: WebViewLocalConfig? = null,

    /**
     * Crash handler settings
     */
    @Json(name = "crash_handler")
    val crashHandler: CrashHandlerLocalConfig? = null,

    /**
     * Compose settings
     */
    @Json(name = "compose")
    val composeConfig: ComposeLocalConfig? = null,

    /**
     * Whether fcm PII data should be hidden or not
     */
    @Json(name = "capture_fcm_pii_data")
    val captureFcmPiiData: Boolean? = null,

    /**
     * Networking moment settings
     */
    @Json(name = "networking")
    val networking: NetworkLocalConfig? = null,

    @Json(name = "capture_public_key")
    val capturePublicKey: String? = null,

    /**
     * List of strings for sensitive keys that should be redacted when they are sent to the server.
     */
    @Json(name = "sensitive_keys_denylist")
    val sensitiveKeysDenylist: List<String>? = null,

    /**
     * ANR settings
     */
    @Json(name = "anr")
    val anr: AnrLocalConfig? = null,

    /**
     * App settings
     */
    @Json(name = "app")
    val app: AppLocalConfig? = null,

    /**
     * Background activity config settings
     */
    @Json(name = "background_activity")
    val backgroundActivityConfig: BackgroundActivityLocalConfig? = null,

    /**
     * Base URL settings
     */
    @Json(name = "base_urls")
    val baseUrls: BaseUrlLocalConfig? = null,

    /**
     * Whether signal handler detection should be enabled or not
     */
    @Json(name = "sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,

    /**
     * Background activity config settings
     */
    @Json(name = "app_exit_info")
    val appExitInfoConfig: AppExitInfoLocalConfig? = null,

    @Json(name = "app_framework")
    val appFramework: String? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
