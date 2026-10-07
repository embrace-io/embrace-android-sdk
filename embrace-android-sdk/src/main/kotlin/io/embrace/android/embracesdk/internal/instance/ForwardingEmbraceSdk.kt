@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.instance

import android.app.Activity
import io.embrace.android.embracesdk.EmbraceSdk
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
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
 * An [EmbraceSdk] that resolves its target on every call. Interface members with default bodies are not
 * overridden: they dispatch back through the abstract members below.
 */
internal abstract class ForwardingEmbraceSdk : EmbraceSdk {

    protected abstract fun delegate(): EmbraceSdk

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>) =
        delegate().logMessage(message, severity, properties)

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>, attachment: ByteArray) =
        delegate().logMessage(message, severity, properties, attachment)

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachmentId: String,
        attachmentUrl: String,
    ) = delegate().logMessage(message, severity, properties, attachmentId, attachmentUrl)

    override fun logInfo(message: String) = delegate().logInfo(message)

    override fun logWarning(message: String) = delegate().logWarning(message)

    override fun logError(message: String) = delegate().logError(message)

    override fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>, message: String?) =
        delegate().logException(throwable, severity, properties, message)

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) = delegate().logCustomStacktrace(stacktraceElements, severity, properties, message)

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
    ) = delegate().logPushNotification(
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
        delegate().recordNetworkRequest(networkRequest)

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) =
        delegate().addHttpRequestInfoModifier(modifier)

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) =
        delegate().removeHttpRequestInfoModifier(modifier)

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = delegate().generateW3cTraceparent()

    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean =
        delegate().addUserSessionProperty(key, value, scope)

    override fun removeUserSessionProperty(key: String): Boolean = delegate().removeUserSessionProperty(key)

    override fun endUserSession() = delegate().endUserSession()

    override fun addUserSessionListener(listener: UserSessionListener) = delegate().addUserSessionListener(listener)

    override fun removeUserSessionListener(listener: UserSessionListener) =
        delegate().removeUserSessionListener(listener)

    override fun setUserIdentifier(userId: String?) = delegate().setUserIdentifier(userId)

    override fun clearUserIdentifier() = delegate().clearUserIdentifier()

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) = delegate().setUserEmail(email)

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() = delegate().clearUserEmail()

    override fun addUserPersona(persona: String) = delegate().addUserPersona(persona)

    override fun clearUserPersona(persona: String) = delegate().clearUserPersona(persona)

    override fun clearAllUserPersonas() = delegate().clearAllUserPersonas()

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) = delegate().setUsername(username)

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() = delegate().clearUsername()

    override fun createSpan(name: String, parent: EmbraceSpan?, autoTerminationMode: AutoTerminationMode): EmbraceSpan =
        delegate().createSpan(name, parent, autoTerminationMode)

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan = delegate().startSpan(name, parent, startTimeMs, autoTerminationMode)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T = delegate().recordSpan(name, parent, attributes, events, autoTerminationMode, code)

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean = delegate().recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events)

    override fun getSpan(spanId: String): EmbraceSpan? = delegate().getSpan(spanId)

    override fun startView(name: String): Boolean = delegate().startView(name)

    override fun endView(name: String): Boolean = delegate().endView(name)

    override fun disable() = delegate().disable()

    override fun applicationInitStart() = delegate().applicationInitStart()

    override fun applicationInitEnd() = delegate().applicationInitEnd()

    override val isStarted: Boolean get() = delegate().isStarted

    override val deviceId: String get() = delegate().deviceId

    override val currentUserSessionId: String? get() = delegate().currentUserSessionId

    override val lastRunEndState: LastRunEndState get() = delegate().lastRunEndState

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) =
        delegate().addLogRecordExporter(logRecordExporter)

    override fun addSpanExporter(spanExporter: SpanExporter) = delegate().addSpanExporter(spanExporter)

    override fun addSpanProcessor(spanProcessor: SpanProcessor) = delegate().addSpanProcessor(spanProcessor)

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) =
        delegate().addLogRecordProcessor(logRecordProcessor)

    override fun getOpenTelemetryKotlin(): OpenTelemetry = delegate().getOpenTelemetryKotlin()

    override fun setResourceAttribute(key: String, value: String) = delegate().setResourceAttribute(key, value)

    override fun addBreadcrumb(message: String) = delegate().addBreadcrumb(message)

    override fun appReady() = delegate().appReady()

    override fun activityLoaded(activity: Activity) = delegate().activityLoaded(activity)

    override fun getSdkCurrentTimeMs(): Long = delegate().getSdkCurrentTimeMs()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) =
        delegate().addLoadTraceAttribute(activity, key, value)

    override fun addLoadTraceChildSpan(
        activity: Activity,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) = delegate().addLoadTraceChildSpan(activity, name, startTimeMs, endTimeMs, attributes, events, errorCode)

    override fun addStartupTraceAttribute(key: String, value: String) = delegate().addStartupTraceAttribute(key, value)

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) = delegate().addStartupTraceChildSpan(name, startTimeMs, endTimeMs, attributes, events, errorCode)

    override fun observeNavigation(activity: Activity, navigationController: Any) =
        delegate().observeNavigation(activity, navigationController)

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        delegate().createExperiment(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) = delegate().trackExperiments(experiments)

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) = delegate().untrackExperiments(ids, endedAt)

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        delegate().createFeatureFlag(id, variant, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) = delegate().trackFeatureFlags(flags)

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) = delegate().untrackFeatureFlags(ids, endedAt)
}
