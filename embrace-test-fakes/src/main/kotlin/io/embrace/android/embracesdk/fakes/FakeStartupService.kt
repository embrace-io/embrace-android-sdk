package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState

class FakeStartupService : StartupService {

    var sdkStartupDurationImpl: Long? = null
    var appState: AppState? = null
    var threadName: String? = null

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: AppState,
        threadName: String,
    ) {
        sdkStartupDurationImpl = endTimeMs - startTimeMs
        this.appState = endState
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

    override fun getInitThreadName(): String? = threadName
}
