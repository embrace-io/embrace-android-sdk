package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.startup.StartupService

public class FakeStartupService : StartupService {

    public var sdkStartupDuration: Long? = null
    public var endedInForeground: Boolean? = null
    public var threadName: String? = null

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endedInForeground: Boolean,
        threadName: String
    ) {
        sdkStartupDuration = endTimeMs - startTimeMs
        this.endedInForeground = endedInForeground
        this.threadName = threadName
    }

    override fun getSdkStartupDuration(coldStart: Boolean): Long? {
        return sdkStartupDuration
    }

    override fun getSdkInitStartMs(): Long? {
        TODO("Not yet implemented")
    }

    override fun getSdkInitEndMs(): Long? {
        TODO("Not yet implemented")
    }

    override fun endedInForeground(): Boolean? = endedInForeground

    override fun getInitThreadName(): String? = threadName
}
