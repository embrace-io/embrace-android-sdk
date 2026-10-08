@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.instance

import android.app.Activity
import android.content.Context
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.api.delegate.TrackedExperimentImpl
import io.embrace.android.embracesdk.internal.api.delegate.TrackedFeatureFlagImpl
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

/**
 * A no-op implementation of [SdkApi]. The first API call that gets dropped logs [message] along
 * with the name of the action that could not be performed.
 */
internal class NoopSdkInstance(
    private val logger: InternalLogger,
    private val message: String,
) : SdkApi {

    companion object {
        const val SDK_NOT_INITIALIZED: String = "Embrace SDK is not initialized yet"
        const val SDK_DISABLED: String = "Embrace SDK is disabled"
    }

    @Volatile
    private var logged: Boolean = false

    private fun logDropped(action: String) {
        if (!logged) {
            logged = true
            val msg = "$message, cannot $action. Subsequent dropped calls will not be logged."
            logger.logInfo(msg, Throwable(msg))
        }
    }

    override fun start(context: Context) {}

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>) {
        logDropped("log_message")
    }

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>, attachment: ByteArray) {
        logDropped("log_message")
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachmentId: String,
        attachmentUrl: String,
    ) {
        logDropped("log_message")
    }

    override fun logInfo(message: String) {
        logDropped("log_message")
    }

    override fun logWarning(message: String) {
        logDropped("log_message")
    }

    override fun logError(message: String) {
        logDropped("log_message")
    }

    override fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>, message: String?) {
        logDropped("log_exception")
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) {
        logDropped("log_custom_stacktrace")
    }

    @Deprecated("This API is deprecated and will be removed in a future release.Use logMessage() instead.")
    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int?,
        isNotification: Boolean?,
        hasData: Boolean?,
    ) {
        logDropped("log_push_notification")
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        logDropped("record_network_request")
    }

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        logDropped("add_http_request_info_modifier")
    }

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        logDropped("remove_http_request_info_modifier")
    }

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = null

    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean {
        logDropped("add_user_session_property")
        return false
    }

    override fun removeUserSessionProperty(key: String): Boolean {
        logDropped("remove_user_session_property")
        return false
    }

    override fun endUserSession() {
        logDropped("end_user_session")
    }

    override fun addUserSessionListener(listener: UserSessionListener) {
        logDropped("add_user_session_listener")
    }

    override fun removeUserSessionListener(listener: UserSessionListener) {
        logDropped("remove_user_session_listener")
    }

    override fun setUserIdentifier(userId: String?) {
        logDropped("set_user_identifier")
    }

    override fun clearUserIdentifier() {
        logDropped("clear_user_identifier")
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) {
        logDropped("set_user_email")
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() {
        logDropped("clear_user_email")
    }

    override fun addUserPersona(persona: String) {
        logDropped("add_user_persona")
    }

    override fun clearUserPersona(persona: String) {
        logDropped("clear_user_persona")
    }

    override fun clearAllUserPersonas() {
        logDropped("clear_all_user_personas")
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) {
        logDropped("set_username")
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() {
        logDropped("clear_username")
    }

    override fun createSpan(name: String, parent: EmbraceSpan?, autoTerminationMode: AutoTerminationMode): EmbraceSpan {
        logDropped("create_span")
        return NoopEmbraceSdkSpan
    }

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan {
        logDropped("start_span")
        return NoopEmbraceSdkSpan
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T {
        logDropped("record_span")
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean {
        logDropped("record_completed_span")
        return false
    }

    override fun getSpan(spanId: String): EmbraceSpan? {
        logDropped("get_span")
        return null
    }

    override fun startView(name: String): Boolean {
        logDropped("start_view")
        return false
    }

    override fun endView(name: String): Boolean {
        logDropped("end_view")
        return false
    }

    override fun disable() {}

    override fun applicationInitStart() {}

    override fun applicationInitEnd() {}

    override val isStarted: Boolean = false

    override val deviceId: String = ""

    override val currentUserSessionId: String? = null

    override val lastRunEndState: LastRunEndState = LastRunEndState.INVALID

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        logDropped("add_log_record_exporter")
    }

    override fun addSpanExporter(spanExporter: SpanExporter) {
        logDropped("add_span_exporter")
    }

    override fun addSpanProcessor(spanProcessor: SpanProcessor) {
        logDropped("add_span_processor")
    }

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
        logDropped("add_log_record_processor")
    }

    override fun getOpenTelemetryKotlin(): OpenTelemetry {
        logDropped("get_opentelemetry_kotlin")
        return NoopOpenTelemetry
    }

    override fun setResourceAttribute(key: String, value: String) {
        logDropped("set_resource_attribute")
    }

    override fun addBreadcrumb(message: String) {
        logDropped("add_breadcrumb")
    }

    override fun appReady() {
        logDropped("app_ready")
    }

    override fun activityLoaded(activity: Activity) {
        logDropped("activity_fully_loaded")
    }

    override fun getSdkCurrentTimeMs(): Long = System.currentTimeMillis()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) {
        logDropped("add_load_trace_attribute")
    }

    override fun addLoadTraceChildSpan(
        activity: Activity,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        logDropped("add_load_trace_child_span")
    }

    override fun addStartupTraceAttribute(key: String, value: String) {
        logDropped("add_startup_trace_attribute")
    }

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        logDropped("add_startup_trace_child_span")
    }

    override fun observeNavigation(activity: Activity, navigationController: Any) {
        logDropped("observe_navigation")
    }

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) {
        logDropped("track_experiments")
    }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {
        logDropped("untrack_experiments")
    }

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, variant, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {
        logDropped("track_feature_flags")
    }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {
        logDropped("untrack_feature_flags")
    }
}
