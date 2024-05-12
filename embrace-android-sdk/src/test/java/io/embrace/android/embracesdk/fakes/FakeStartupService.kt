package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.startup.StartupService

internal class FakeStartupService : StartupService {

    var sdkStartupDuration: Long? = null
    var endedInForeground: Boolean? = null
    var threadName: String? = null

    override fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long, endedInForeground: Boolean, threadName: String?) {
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
}
