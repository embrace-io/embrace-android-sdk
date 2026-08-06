package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import java.util.concurrent.atomic.AtomicInteger

class FakeProcessStateListener : ProcessStateListener {

    val foregroundCount: AtomicInteger = AtomicInteger(0)
    val backgroundCount: AtomicInteger = AtomicInteger(0)

    override fun onBackground() {
        backgroundCount.incrementAndGet()
    }

    override fun onForeground() {
        foregroundCount.incrementAndGet()
    }
}
