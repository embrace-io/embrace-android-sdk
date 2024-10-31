package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.startup.StartupService

class FakeStartupService : StartupService {

    var sdkStartupDurationImpl: Long? = null
    var endedInForeground: Boolean? = null
    var threadName: String? = null

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endedInForeground: Boolean,
        threadName: String,
    ) {
        sdkStartupDurationImpl = endTimeMs - startTimeMs
        this.endedInForeground = endedInForeground
        this.threadName = threadName
    }

    override fun getSdkStartupDuration(): Long? {
        return sdkStartupDurationImpl
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
