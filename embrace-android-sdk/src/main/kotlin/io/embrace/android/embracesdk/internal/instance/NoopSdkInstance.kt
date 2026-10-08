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
 * A no-op implementation of [SdkApi].
 */
internal object NoopSdkInstance : SdkApi {

    override fun start(context: Context) {}

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>) {}

    override fun logMessage(message: String, severity: Severity, properties: Map<String, Any>, attachment: ByteArray) {}

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachmentId: String,
        attachmentUrl: String,
    ) {
    }

    override fun logInfo(message: String) {}

    override fun logWarning(message: String) {}

    override fun logError(message: String) {}

    override fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>, message: String?) {}

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) {
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
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {}

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {}

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {}

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = null

    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean = false

    override fun removeUserSessionProperty(key: String): Boolean = false

    override fun endUserSession() {}

    override fun addUserSessionListener(listener: UserSessionListener) {}

    override fun removeUserSessionListener(listener: UserSessionListener) {}

    override fun setUserIdentifier(userId: String?) {}

    override fun clearUserIdentifier() {}

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) {}

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() {}

    override fun addUserPersona(persona: String) {}

    override fun clearUserPersona(persona: String) {}

    override fun clearAllUserPersonas() {}

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) {}

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() {}

    override fun createSpan(name: String, parent: EmbraceSpan?, autoTerminationMode: AutoTerminationMode): EmbraceSpan =
        NoopEmbraceSdkSpan

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan = NoopEmbraceSdkSpan

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T = code()

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean = false

    override fun getSpan(spanId: String): EmbraceSpan? = null

    override fun startView(name: String): Boolean = false

    override fun endView(name: String): Boolean = false

    override fun disable() {}

    override fun applicationInitStart() {}

    override fun applicationInitEnd() {}

    override val isStarted: Boolean = false

    override val deviceId: String = ""

    override val currentUserSessionId: String? = null

    override val lastRunEndState: LastRunEndState = LastRunEndState.INVALID

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {}

    override fun addSpanExporter(spanExporter: SpanExporter) {}

    override fun addSpanProcessor(spanProcessor: SpanProcessor) {}

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {}

    override fun getOpenTelemetryKotlin(): OpenTelemetry = NoopOpenTelemetry

    override fun setResourceAttribute(key: String, value: String) {}

    override fun addBreadcrumb(message: String) {}

    override fun appReady() {}

    override fun activityLoaded(activity: Activity) {}

    override fun getSdkCurrentTimeMs(): Long = System.currentTimeMillis()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) {}

    override fun addLoadTraceChildSpan(
        activity: Activity,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
    }

    override fun addStartupTraceAttribute(key: String, value: String) {}

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
    }

    override fun observeNavigation(activity: Activity, navigationController: Any) {}

    override fun createExperiment(id: String, variant: String?, startedAt: Long?): TrackedExperiment =
        TrackedExperimentImpl(id, variant, startedAt)

    override fun trackExperiments(experiments: List<TrackedExperiment>) {}

    override fun untrackExperiments(ids: List<String>, endedAt: Long?) {}

    override fun createFeatureFlag(id: String, variant: String?, startedAt: Long?): TrackedFeatureFlag =
        TrackedFeatureFlagImpl(id, variant, startedAt)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlag>) {}

    override fun untrackFeatureFlags(ids: List<String>, endedAt: Long?) {}
}
