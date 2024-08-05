package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector

public class FakeAppStartupDataCollector(
    private val clock: FakeClock,
) : AppStartupDataCollector {
    public var applicationInitStartMs: Long? = null
    public var applicationInitEndMs: Long? = null
    public var startupActivityName: String? = null
    public var startupActivityPreCreatedMs: Long? = null
    public var startupActivityInitStartMs: Long? = null
    public var startupActivityPostCreatedMs: Long? = null
    public var startupActivityInitEndMs: Long? = null
    public var startupActivityResumedMs: Long? = null
    public var firstFrameRenderedMs: Long? = null

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
