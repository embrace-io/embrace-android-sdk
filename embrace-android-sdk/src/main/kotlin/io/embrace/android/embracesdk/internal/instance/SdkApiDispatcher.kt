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
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

/**
 * An [SdkApi] implementation that forwards every call to [target]. [target] is volatile and read fresh on
 * every call; it must only ever be changed by [SdkStateHolder].
 */
internal class SdkApiDispatcher(
    @Volatile var target: SdkApi,
    private val telemetryService: InternalTelemetryService,
) : SdkApi {

    override fun start(context: Context) = target.start(context)

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>) =
        record("log_message") { target.logMessage(message, severity, properties) }

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>, attachment: ByteArray) =
        record("log_message") { target.logMessage(message, severity, properties, attachment) }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachmentId: String,
        attachmentUrl: String,
    ) = record("log_message") { target.logMessage(message, severity, properties, attachmentId, attachmentUrl) }

    override fun logInfo(message: String) = record("log_message") { target.logInfo(message) }

    override fun logWarning(message: String) = record("log_message") { target.logWarning(message) }

    override fun logError(message: String) = record("log_message") { target.logError(message) }

    override fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>, message: String?) =
        record("log_message") { target.logException(throwable, severity, properties, message) }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) = record("log_message") { target.logCustomStacktrace(stacktraceElements, severity, properties, message) }

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
    ) = record("log_push_notification") {
        target.logPushNotification(
            title,
            body,
            topic,
            id,
            notificationPriority,
            messageDeliveredPriority,
            isNotification,
            hasData,
        )
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) =
        record("record_network_request") { target.recordNetworkRequest(networkRequest) }

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) =
        record("add_http_request_info_modifier") { target.addHttpRequestInfoModifier(modifier) }

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) =
        record("remove_http_request_info_modifier") { target.removeHttpRequestInfoModifier(modifier) }

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = target.generateW3cTraceparent()

    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean =
        record("add_session_property") { target.addUserSessionProperty(key, value, scope) }

    override fun removeUserSessionProperty(key: String): Boolean =
        record("remove_session_property") { target.removeUserSessionProperty(key) }

    override fun endUserSession() = record("end_session") { target.endUserSession() }

    override fun addUserSessionListener(listener: UserSessionListener) =
        record("add_user_session_listener") { target.addUserSessionListener(listener) }

    override fun removeUserSessionListener(listener: UserSessionListener) =
        record("remove_user_session_listener") { target.removeUserSessionListener(listener) }

    override fun setUserIdentifier(userId: String?) = record("set_user_identifier") { target.setUserIdentifier(userId) }

    override fun clearUserIdentifier() = record("clear_user_identifier") { target.clearUserIdentifier() }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) = record("set_user_email") { target.setUserEmail(email) }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() = record("clear_user_email") { target.clearUserEmail() }

    override fun addUserPersona(persona: String) = record("add_user_persona") { target.addUserPersona(persona) }

    override fun clearUserPersona(persona: String) = record("clear_user_persona") { target.clearUserPersona(persona) }

    override fun clearAllUserPersonas() = record("clear_user_personas") { target.clearAllUserPersonas() }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) = record("set_username") { target.setUsername(username) }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() = record("clear_username") { target.clearUsername() }

    override fun createSpan(name: String, parent: EmbraceSpan?, autoTerminationMode: AutoTerminationMode): EmbraceSpan =
        target.createSpan(name, parent, autoTerminationMode)

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan = target.startSpan(name, parent, startTimeMs, autoTerminationMode)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T = target.recordSpan(name, parent, attributes, events, autoTerminationMode, code)

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean = target.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events)

    override fun getSpan(spanId: String): EmbraceSpan? = target.getSpan(spanId)

    override fun startView(name: String): Boolean = record("start_view") { target.startView(name) }

    override fun endView(name: String): Boolean = record("end_view") { target.endView(name) }

    override fun disable() = target.disable()

    override fun applicationInitStart() = target.applicationInitStart()

    override fun applicationInitEnd() = record("application_init_end") { target.applicationInitEnd() }

    override val isStarted: Boolean get() = target.isStarted

    override val deviceId: String get() = record("get_device_id") { target.deviceId }

    override val currentUserSessionId: String? get() = record("get_current_session_id") { target.currentUserSessionId }

    override val lastRunEndState: LastRunEndState get() = target.lastRunEndState

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) =
        target.addLogRecordExporter(logRecordExporter)

    override fun addSpanExporter(spanExporter: SpanExporter) = target.addSpanExporter(spanExporter)

    override fun addSpanProcessor(spanProcessor: SpanProcessor) = target.addSpanProcessor(spanProcessor)

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) =
        target.addLogRecordProcessor(logRecordProcessor)

    override fun getOpenTelemetryKotlin(): OpenTelemetry = target.getOpenTelemetryKotlin()

    override fun setResourceAttribute(key: String, value: String) = target.setResourceAttribute(key, value)

    override fun addBreadcrumb(message: String) = record("add_breadcrumb") { target.addBreadcrumb(message) }

    override fun appReady() = record("app_ready") { target.appReady() }

    override fun activityLoaded(activity: Activity) = record("activity_fully_loaded") { target.activityLoaded(activity) }

    override fun getSdkCurrentTimeMs(): Long = target.getSdkCurrentTimeMs()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) =
        record("add_load_trace_attribute") { target.addLoadTraceAttribute(activity, key, value) }

    override fun addLoadTraceChildSpan(activity: Activity, name: String, startTimeMs: Long, endTimeMs: Long) =
        record("add_load_trace_child_span") { target.addLoadTraceChildSpan(activity, name, startTimeMs, endTimeMs) }

    override fun addLoadTraceChildSpan(
        activity: Activity,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) = record("add_load_trace_child_span") {
        target.addLoadTraceChildSpan(activity, name, startTimeMs, endTimeMs, attributes, events, errorCode)
    }

    override fun addStartupTraceAttribute(key: String, value: String) =
        record("add_startup_trace_attribute") { target.addStartupTraceAttribute(key, value) }

    override fun addStartupTraceChildSpan(name: String, startTimeMs: Long, endTimeMs: Long) =
        record("add_startup_trace_child_span") { target.addStartupTraceChildSpan(name, startTimeMs, endTimeMs) }

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) = record("add_startup_trace_child_span") {
        target.addStartupTraceChildSpan(name, startTimeMs, endTimeMs, attributes, events, errorCode)
    }

    override fun observeNavigation(activity: Activity, navigationController: Any) =
        record("observe_navigation") { target.observeNavigation(activity, navigationController) }

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        target.createExperiment(id, variant, startedAt)

    override fun trackExperiment(id: String, variant: String?, startedAt: Long?) =
        record("track_experiment") { target.trackExperiment(id, variant, startedAt) }

    override fun trackExperiments(experiments: List<TrackedExperiment>) =
        record("track_experiment") { target.trackExperiments(experiments) }

    override fun untrackExperiment(id: String, endedAt: Long?) =
        record("untrack_experiment") { target.untrackExperiment(id, endedAt) }

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) =
        record("untrack_experiment") { target.untrackExperiments(ids, endedAt) }

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        target.createFeatureFlag(id, variant, startedAt)

    override fun trackFeatureFlag(id: String, variant: String?, startedAt: Long?) =
        record("track_feature_flag") { target.trackFeatureFlag(id, variant, startedAt) }

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) =
        record("track_feature_flag") { target.trackFeatureFlags(flags) }

    override fun untrackFeatureFlag(id: String, endedAt: Long?) =
        record("untrack_feature_flag") { target.untrackFeatureFlag(id, endedAt) }

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) =
        record("untrack_feature_flag") { target.untrackFeatureFlags(ids, endedAt) }

    /**
     * Records usage of the public API [name] then performs [call].
     */
    private inline fun <T> record(name: String, call: () -> T): T {
        telemetryService.onPublicApiCalled(name)
        return call()
    }
}
