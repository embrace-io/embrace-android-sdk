package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute.UserAbandon.fromErrorCode
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.fromMap
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class FakeAppStartupDataCollector(
    private val clock: FakeClock,
) : AppStartupDataCollector {
    var applicationInitStartMs: Long? = null
    var applicationInitEndMs: Long? = null
    var startupActivityName: String? = null
    var firstActivityInitMs: Long? = null
    var startupActivityPreCreatedMs: Long? = null
    var startupActivityInitStartMs: Long? = null
    var startupActivityPostCreatedMs: Long? = null
    var startupActivityInitEndMs: Long? = null
    var startupActivityResumedMs: Long? = null
    var firstFrameRenderedMs: Long? = null
    var appReadyMs: Long? = null
    var appStartupCompleteCallback: (() -> Unit)? = null
    var customChildSpans = ConcurrentLinkedQueue<SpanData>()
    var customAttributes: MutableMap<String, String> = ConcurrentHashMap()
    var dataValidator: DataValidator = DataValidator()

    override fun applicationInitStart(timestampMs: Long?) {
        applicationInitStartMs = timestampMs ?: clock.now()
    }

    override fun applicationInitEnd(timestampMs: Long?) {
        applicationInitEndMs = timestampMs ?: clock.now()
    }

    override fun firstActivityInit(timestampMs: Long?, startupCompleteCallback: () -> Unit) {
        firstActivityInitMs = timestampMs ?: clock.now()
        appStartupCompleteCallback = startupCompleteCallback
    }

    override fun startupActivityPreCreated(timestampMs: Long?) {
        startupActivityPreCreatedMs = timestampMs ?: clock.now()
    }

    override fun startupActivityInitStart(timestampMs: Long?) {
        startupActivityInitStartMs = timestampMs ?: clock.now()
    }

    override fun startupActivityPostCreated(timestampMs: Long?) {
        startupActivityPostCreatedMs = timestampMs ?: clock.now()
    }

    override fun startupActivityInitEnd(timestampMs: Long?) {
        startupActivityInitEndMs = timestampMs ?: clock.now()
    }

    override fun startupActivityResumed(
        activityName: String,
        timestampMs: Long?,
    ) {
        startupActivityName = activityName
        startupActivityResumedMs = timestampMs ?: clock.now()
        appStartupCompleteCallback?.invoke()
    }

    override fun firstFrameRendered(
        activityName: String,
        timestampMs: Long?,
    ) {
        startupActivityName = activityName
        firstFrameRenderedMs = timestampMs ?: clock.now()
        appStartupCompleteCallback?.invoke()
    }

    override fun appReady(timestampMs: Long?) {
        appReadyMs = timestampMs ?: clock.now()
        appStartupCompleteCallback?.invoke()
    }

    override fun addTrackedInterval(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        val attributesBuilder = Attributes.builder().fromMap(
            attributes = attributes,
            internal = false,
            limitsValidator = dataValidator
        )
        val status = if (errorCode != null) {
            val errorCodeAttr = errorCode.fromErrorCode()
            attributesBuilder.put(errorCodeAttr.key.name, errorCodeAttr.value)
            StatusData.error()
        } else {
            StatusData.unset()
        }
        customChildSpans.add(
            FakeSpanData(
                name = name,
                startEpochNanos = startTimeMs.millisToNanos(),
                endTimeNanos = endTimeMs.millisToNanos(),
                attributes = attributesBuilder.build(),
                events = events.map {
                    EventData.create(
                        it.timestampNanos,
                        it.name,
                        Attributes.builder().fromMap(
                            attributes = it.attributes,
                            internal = false,
                            limitsValidator = dataValidator
                        ).build()
                    )
                }.toMutableList(),
                spanStatus = status
            )
        )
    }

    override fun addAttribute(key: String, value: String) {
        customAttributes[key] = value
    }
}
