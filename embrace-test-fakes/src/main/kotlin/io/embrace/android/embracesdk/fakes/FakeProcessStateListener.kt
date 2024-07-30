package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import java.util.concurrent.atomic.AtomicInteger

public class FakeProcessStateListener : ProcessStateListener {
    public var coldStart: Boolean = false
    public var timestamp: Long = -1

    public val foregroundCount: AtomicInteger = AtomicInteger(0)
    public val backgroundCount: AtomicInteger = AtomicInteger(0)

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
