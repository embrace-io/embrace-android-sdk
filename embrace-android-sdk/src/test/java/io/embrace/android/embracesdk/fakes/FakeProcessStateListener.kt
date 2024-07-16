package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import java.util.concurrent.atomic.AtomicInteger

internal class FakeProcessStateListener : ProcessStateListener {
    var coldStart: Boolean = false
    var timestamp: Long = -1

    val foregroundCount = AtomicInteger(0)
    val backgroundCount = AtomicInteger(0)

    override fun onBackground(timestamp: Long) {
        this.timestamp = timestamp
        backgroundCount.incrementAndGet()
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        this.coldStart = coldStart
        this.timestamp = timestamp
        foregroundCount.incrementAndGet()
    }
}
