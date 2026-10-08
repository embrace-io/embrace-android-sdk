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
) : SdkApi {

    override fun start(context: Context) = target.start(context)

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>) =
        target.logMessage(message, severity, properties)

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>, attachment: ByteArray) =
        target.logMessage(message, severity, properties, attachment)

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachmentId: String,
        attachmentUrl: String,
    ) = target.logMessage(message, severity, properties, attachmentId, attachmentUrl)

    override fun logInfo(message: String) = target.logInfo(message)

    override fun logWarning(message: String) = target.logWarning(message)

    override fun logError(message: String) = target.logError(message)

    override fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>, message: String?) =
        target.logException(throwable, severity, properties, message)

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) = target.logCustomStacktrace(stacktraceElements, severity, properties, message)

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
    ) = target.logPushNotification(
        title,
        body,
        topic,
        id,
        notificationPriority,
        messageDeliveredPriority,
        isNotification,
        hasData,
    )

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) =
        target.recordNetworkRequest(networkRequest)

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) =
        target.addHttpRequestInfoModifier(modifier)

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) =
        target.removeHttpRequestInfoModifier(modifier)

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = target.generateW3cTraceparent()

    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean =
        target.addUserSessionProperty(key, value, scope)

    override fun removeUserSessionProperty(key: String): Boolean = target.removeUserSessionProperty(key)

    override fun endUserSession() = target.endUserSession()

    override fun addUserSessionListener(listener: UserSessionListener) = target.addUserSessionListener(listener)

    override fun removeUserSessionListener(listener: UserSessionListener) =
        target.removeUserSessionListener(listener)

    override fun setUserIdentifier(userId: String?) = target.setUserIdentifier(userId)

    override fun clearUserIdentifier() = target.clearUserIdentifier()

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) = target.setUserEmail(email)

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() = target.clearUserEmail()

    override fun addUserPersona(persona: String) = target.addUserPersona(persona)

    override fun clearUserPersona(persona: String) = target.clearUserPersona(persona)

    override fun clearAllUserPersonas() = target.clearAllUserPersonas()

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) = target.setUsername(username)

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() = target.clearUsername()

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

    override fun startView(name: String): Boolean = target.startView(name)

    override fun endView(name: String): Boolean = target.endView(name)

    override fun disable() = target.disable()

    override fun applicationInitStart() = target.applicationInitStart()

    override fun applicationInitEnd() = target.applicationInitEnd()

    override val isStarted: Boolean get() = target.isStarted

    override val deviceId: String get() = target.deviceId

    override val currentUserSessionId: String? get() = target.currentUserSessionId

    override val lastRunEndState: LastRunEndState get() = target.lastRunEndState

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) =
        target.addLogRecordExporter(logRecordExporter)

    override fun addSpanExporter(spanExporter: SpanExporter) = target.addSpanExporter(spanExporter)

    override fun addSpanProcessor(spanProcessor: SpanProcessor) = target.addSpanProcessor(spanProcessor)

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) =
        target.addLogRecordProcessor(logRecordProcessor)

    override fun getOpenTelemetryKotlin(): OpenTelemetry = target.getOpenTelemetryKotlin()

    override fun setResourceAttribute(key: String, value: String) = target.setResourceAttribute(key, value)

    override fun addBreadcrumb(message: String) = target.addBreadcrumb(message)

    override fun appReady() = target.appReady()

    override fun activityLoaded(activity: Activity) = target.activityLoaded(activity)

    override fun getSdkCurrentTimeMs(): Long = target.getSdkCurrentTimeMs()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) =
        target.addLoadTraceAttribute(activity, key, value)

    override fun addLoadTraceChildSpan(activity: Activity, name: String, startTimeMs: Long, endTimeMs: Long) =
        target.addLoadTraceChildSpan(activity, name, startTimeMs, endTimeMs)

    override fun addLoadTraceChildSpan(
        activity: Activity,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) = target.addLoadTraceChildSpan(activity, name, startTimeMs, endTimeMs, attributes, events, errorCode)

    override fun addStartupTraceAttribute(key: String, value: String) = target.addStartupTraceAttribute(key, value)

    override fun addStartupTraceChildSpan(name: String, startTimeMs: Long, endTimeMs: Long) =
        target.addStartupTraceChildSpan(name, startTimeMs, endTimeMs)

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) = target.addStartupTraceChildSpan(name, startTimeMs, endTimeMs, attributes, events, errorCode)

    override fun observeNavigation(activity: Activity, navigationController: Any) =
        target.observeNavigation(activity, navigationController)

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        target.createExperiment(id, variant, startedAt)

    override fun trackExperiment(id: String, variant: String?, startedAt: Long?) =
        target.trackExperiment(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) = target.trackExperiments(experiments)

    override fun untrackExperiment(id: String, endedAt: Long?) = target.untrackExperiment(id, endedAt)

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) = target.untrackExperiments(ids, endedAt)

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        target.createFeatureFlag(id, variant, startedAt)

    override fun trackFeatureFlag(id: String, variant: String?, startedAt: Long?) =
        target.trackFeatureFlag(id, variant, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) = target.trackFeatureFlags(flags)

    override fun untrackFeatureFlag(id: String, endedAt: Long?) = target.untrackFeatureFlag(id, endedAt)

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) = target.untrackFeatureFlags(ids, endedAt)
}
