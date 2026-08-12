package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService

class FakeStartupService : StartupService {

    var sdkStartupDurationImpl: Long? = null
    var processState: ProcessState? = null
    var threadName: String? = null
    var appVersionStartupCounterImpl: Int? = null
    var sdkInitDurationsImpl: Map<String, Long> = emptyMap()

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: ProcessState,
        threadName: String,
        sdkInitDurations: Map<String, Long>,
    ) {
        sdkStartupDurationImpl = endTimeMs - startTimeMs
        this.processState = endState
        this.threadName = threadName
        sdkInitDurationsImpl = sdkInitDurations
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

    override fun getSdkInitDurations(): Map<String, Long> = sdkInitDurationsImpl
}
