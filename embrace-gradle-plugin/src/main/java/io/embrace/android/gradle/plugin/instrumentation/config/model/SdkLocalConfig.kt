package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class SdkLocalConfig(
    /**
     * Service enablement config settings
     */
    @Json(name = "automatic_data_capture")
    @SerialName("automatic_data_capture")
    val automaticDataCaptureConfig: AutomaticDataCaptureLocalConfig? = null,

    /**
     * Taps
     */
    @Json(name = "taps")
    @SerialName("taps")
    val taps: TapsLocalConfig? = null,

    /**
     * OpenTelemetry config settings
     */
    @Json(name = "otel")
    @SerialName("otel")
    val otel: OpenTelemetryLocalConfig? = null,

    /**
     * Whether session telemetry should be persisted using the multi-file layout
     */
    @Json(name = "multi_file_persistence_enabled")
    @SerialName("multi_file_persistence_enabled")
    val multiFilePersistenceEnabled: Boolean? = null,

    /**
     * View settings
     */
    @Json(name = "view_config")
    @SerialName("view_config")
    val viewConfig: ViewLocalConfig? = null,

    /**
     * Webview settings
     */
    @Json(name = "webview")
    @SerialName("webview")
    val webViewConfig: WebViewLocalConfig? = null,

    /**
     * Crash handler settings
     */
    @Json(name = "crash_handler")
    @SerialName("crash_handler")
    val crashHandler: CrashHandlerLocalConfig? = null,

    /**
     * Compose settings
     */
    @Json(name = "compose")
    @SerialName("compose")
    val composeConfig: ComposeLocalConfig? = null,

    /**
     * Whether fcm PII data should be hidden or not
     */
    @Json(name = "capture_fcm_pii_data")
    @SerialName("capture_fcm_pii_data")
    val captureFcmPiiData: Boolean? = null,

    /**
     * Networking moment settings
     */
    @Json(name = "networking")
    @SerialName("networking")
    val networking: NetworkLocalConfig? = null,

    @Json(name = "capture_public_key")
    @SerialName("capture_public_key")
    val capturePublicKey: String? = null,

    /**
     * List of strings for sensitive keys that should be redacted when they are sent to the server.
     */
    @Json(name = "sensitive_keys_denylist")
    @SerialName("sensitive_keys_denylist")
    val sensitiveKeysDenylist: List<String>? = null,

    /**
     * ANR settings
     */
    @Json(name = "anr")
    @SerialName("anr")
    val threadBlockage: ThreadBlockageLocalConfig? = null,

    /**
     * App settings
     */
    @Json(name = "app")
    @SerialName("app")
    val app: AppLocalConfig? = null,

    /**
     * Background activity config settings
     */
    @Json(name = "background_activity")
    @SerialName("background_activity")
    val backgroundActivityConfig: BackgroundActivityLocalConfig? = null,

    /**
     * Base URL settings
     */
    @Json(name = "base_urls")
    @SerialName("base_urls")
    val baseUrls: BaseUrlLocalConfig? = null,

    /**
     * Whether signal handler detection should be enabled or not
     */
    @Json(name = "sig_handler_detection")
    @SerialName("sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,

    /**
     * Background activity config settings
     */
    @Json(name = "app_exit_info")
    @SerialName("app_exit_info")
    val appExitInfoConfig: AppExitInfoLocalConfig? = null,

    @Json(name = "app_framework")
    @SerialName("app_framework")
    val appFramework: String? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
