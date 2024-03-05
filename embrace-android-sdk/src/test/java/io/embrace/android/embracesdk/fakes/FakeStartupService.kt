package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.startup.StartupService

internal class FakeStartupService : StartupService {

    var sdkStartupDuration: Long? = null

    override fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long) {
        sdkStartupDuration = endTimeMs - startTimeMs
    }

    override fun getSdkStartupInfo(coldStart: Boolean): Long? {
        return sdkStartupDuration
    }
}
