package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.process.ProcessInfo

class FakeProcessInfo(private val fakeStartRequestedTime: Long?) : ProcessInfo {
    override fun startRequestedTimeMs(): Long? = fakeStartRequestedTime
}
