package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector

class FakeAppStartupDataCollector(
    private val clock: FakeClock,
) : AppStartupDataCollector {
    var applicationInitStartMs: Long? = null
    var applicationInitEndMs: Long? = null
    var startupActivityName: String? = null
    var startupActivityPreCreatedMs: Long? = null
    var startupActivityInitStartMs: Long? = null
    var startupActivityPostCreatedMs: Long? = null
    var startupActivityInitEndMs: Long? = null
    var startupActivityResumedMs: Long? = null
    var firstFrameRenderedMs: Long? = null

    override fun applicationInitStart(timestampMs: Long?) {
        applicationInitStartMs = timestampMs ?: clock.now()
    }

    override fun applicationInitEnd(timestampMs: Long?) {
        applicationInitEndMs = timestampMs ?: clock.now()
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

    override fun startupActivityResumed(activityName: String, timestampMs: Long?) {
        startupActivityName = activityName
        startupActivityResumedMs = timestampMs ?: clock.now()
    }

    override fun firstFrameRendered(activityName: String, timestampMs: Long?) {
        startupActivityName = activityName
        firstFrameRenderedMs = timestampMs ?: clock.now()
    }

    override fun addTrackedInterval(name: String, startTimeMs: Long, endTimeMs: Long) {
        TODO("Not yet implemented")
    }
}
