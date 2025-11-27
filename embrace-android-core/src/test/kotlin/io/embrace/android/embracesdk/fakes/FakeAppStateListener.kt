package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import java.util.concurrent.atomic.AtomicInteger

class FakeAppStateListener : AppStateListener {

    val foregroundCount: AtomicInteger = AtomicInteger(0)
    val backgroundCount: AtomicInteger = AtomicInteger(0)

    override fun onBackground() {
        backgroundCount.incrementAndGet()
    }

    override fun onForeground() {
        foregroundCount.incrementAndGet()
    }
}
