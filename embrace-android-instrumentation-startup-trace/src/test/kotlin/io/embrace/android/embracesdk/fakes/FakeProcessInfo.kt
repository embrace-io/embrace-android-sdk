package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.startup.ProcessInfo

class FakeProcessInfo(
    private val fakeStartRequestedTime: Long?,
    private val fakeLaunchReason: String? = null,
) : ProcessInfo {
    var prefetchCount: Int = 0
        private set

    override fun startRequestedTimeMs(): Long? = fakeStartRequestedTime
    override fun launchReason(): String? = fakeLaunchReason

    override fun prefetchLaunchReason() {
        prefetchCount++
    }
}
