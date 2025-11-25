package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import java.util.concurrent.ConcurrentHashMap

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
    var customAttributes: MutableMap<String, String> = ConcurrentHashMap()

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
        events: List<SpanEvent>,
        errorCode: ErrorCodeAttribute?,
    ) {
    }

    override fun addAttribute(key: String, value: String) {
        customAttributes[key] = value
    }
}
