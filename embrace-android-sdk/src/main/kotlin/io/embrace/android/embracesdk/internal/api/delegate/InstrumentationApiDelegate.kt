package io.embrace.android.embracesdk.internal.api.delegate

import android.app.Activity
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.traceInstanceId
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

internal class InstrumentationApiDelegate(
    private val clock: Clock,
    private val uiLoadTraceEmitter: UiLoadDataListener?,
    private val appStartupDataCollector: AppStartupDataCollector,
    private val navigationTrackingService: NavigationTrackingService,
) : InstrumentationApi {

    override fun appReady() {
        appStartupDataCollector.appReady(timestampMs = clock.now())
    }

    override fun activityLoaded(activity: Activity) {
        uiLoadTraceEmitter?.complete(traceInstanceId(activity), clock.now())
    }

    override fun getSdkCurrentTimeMs(): Long = clock.now()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) {
        uiLoadTraceEmitter?.addAttribute(traceInstanceId(activity), key, value)
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
        uiLoadTraceEmitter?.addChildSpan(
            instanceId = traceInstanceId(activity),
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            attributes = attributes,
            events = events.map(::toSpanEvent),
            errorCode = errorCode.toErrorCodeAttribute(),
        )
    }

    override fun addStartupTraceAttribute(key: String, value: String) {
        appStartupDataCollector.addAttribute(key, value)
    }

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        appStartupDataCollector.addTrackedInterval(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            attributes = attributes,
            events = events.map(::toSpanEvent),
            errorCode = errorCode?.toErrorCodeAttribute(),
        )
    }

    override fun observeNavigation(activity: Activity, navigationController: Any) {
        navigationTrackingService.trackNavigation(activity, navigationController)
    }

    private fun toSpanEvent(event: EmbraceSpanEvent): SpanEvent = SpanEventImpl(
        name = event.name,
        timestampNanos = event.timestampNanos,
        attributes = event.attributes.toMap(),
    )

    private fun ErrorCode?.toErrorCodeAttribute(): ErrorCodeAttribute? {
        return when (this) {
            ErrorCode.FAILURE -> ErrorCodeAttribute.Failure
            ErrorCode.UNKNOWN -> ErrorCodeAttribute.Unknown
            ErrorCode.USER_ABANDON -> ErrorCodeAttribute.UserAbandon
            null -> null
        }
    }
}
