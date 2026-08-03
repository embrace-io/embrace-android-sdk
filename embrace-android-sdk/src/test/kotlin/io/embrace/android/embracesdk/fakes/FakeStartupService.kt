package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService

class FakeStartupService : StartupService {

    var sdkStartupDurationImpl: Long? = null
    var appState: AppState? = null
    var threadName: String? = null
    var appVersionStartupCounterImpl: Int? = null

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: AppState,
        threadName: String,
        appVersionStartupCounter: Int?,
    ) {
        sdkStartupDurationImpl = endTimeMs - startTimeMs
        this.appState = endState
        this.threadName = threadName
        this.appVersionStartupCounterImpl = appVersionStartupCounter
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

    override fun getAppVersionStartupCounter(): Int? = appVersionStartupCounterImpl
}
