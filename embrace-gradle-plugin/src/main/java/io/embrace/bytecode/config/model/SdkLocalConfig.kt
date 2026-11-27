package io.embrace.bytecode.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SdkLocalConfig(
    /**
     * Service enablement config settings
     */
    @SerialName("automatic_data_capture")
    val automaticDataCaptureConfig: AutomaticDataCaptureLocalConfig? = null,

    /**
     * Taps
     */
    @SerialName("taps")
    val taps: TapsLocalConfig? = null,

    /**
     * OpenTelemetry config settings
     */
    @SerialName("otel")
    val otel: OpenTelemetryLocalConfig? = null,

    /**
     * Whether session telemetry should be persisted using the multi-file layout
     */
    @SerialName("multi_file_persistence_enabled")
    val multiFilePersistenceEnabled: Boolean? = null,

    /**
     * Whether state is recorded as the app state data model: one change log for a session part, a stamp on every log, and
     * each change replayed for the app's own OpenTelemetry log exporters
     */
    @SerialName("shared_state_span_enabled")
    val sharedStateSpanEnabled: Boolean? = null,

    /**
     * Whether spans are linked to state
     */
    @SerialName("state_association_enabled")
    val stateAssociationEnabled: Boolean? = null,

    /**
     * How state is rendered for the app's own OpenTelemetry exporters
     */
    @SerialName("state_export_mode")
    val stateExportMode: String? = null,

    /**
     * View settings
     */
    @SerialName("view_config")
    val viewConfig: ViewLocalConfig? = null,

    /**
     * Webview settings
     */
    @SerialName("webview")
    val webViewConfig: WebViewLocalConfig? = null,

    /**
     * Crash handler settings
     */
    @SerialName("crash_handler")
    val crashHandler: CrashHandlerLocalConfig? = null,

    /**
     * Compose settings
     */
    @SerialName("compose")
    val composeConfig: ComposeLocalConfig? = null,

    /**
     * Whether fcm PII data should be hidden or not
     */
    @SerialName("capture_fcm_pii_data")
    val captureFcmPiiData: Boolean? = null,

    /**
     * Networking moment settings
     */
    @SerialName("networking")
    val networking: NetworkLocalConfig? = null,

    @SerialName("capture_public_key")
    val capturePublicKey: String? = null,

    /**
     * List of strings for sensitive keys that should be redacted when they are sent to the server.
     */
    @SerialName("sensitive_keys_denylist")
    val sensitiveKeysDenylist: List<String>? = null,

    /**
     * ANR settings
     */
    @SerialName("anr")
    val threadBlockage: ThreadBlockageLocalConfig? = null,

    /**
     * App settings
     */
    @SerialName("app")
    val app: AppLocalConfig? = null,

    /**
     * Background activity config settings
     */
    @SerialName("background_activity")
    val backgroundActivityConfig: BackgroundActivityLocalConfig? = null,

    /**
     * Base URL settings
     */
    @SerialName("base_urls")
    val baseUrls: BaseUrlLocalConfig? = null,

    /**
     * Whether signal handler detection should be enabled or not
     */
    @SerialName("sig_handler_detection")
    val sigHandlerDetection: Boolean? = null,

    /**
     * Background activity config settings
     */
    @SerialName("app_exit_info")
    val appExitInfoConfig: AppExitInfoLocalConfig? = null,

    @SerialName("app_framework")
    val appFramework: String? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
