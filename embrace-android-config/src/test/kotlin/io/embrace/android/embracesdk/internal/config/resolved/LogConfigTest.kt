package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().log
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).log
        with(defaults) {
            assertEquals(128, maxMessageLength)
            assertEquals(100, infoLimit)
            assertEquals(200, warnLimit)
            assertEquals(500, errorLimit)
        }
        assertEquals(defaults.snapshot(), resolved.snapshot())
    }

    @Test
    fun `remote values override defaults`() {
        val remote = RemoteConfig(logConfig = LogRemoteConfig(256, 200, 300, 400))
        with(resolveLog(remote)) {
            assertEquals(256, maxMessageLength)
            assertEquals(200, infoLimit)
            assertEquals(300, warnLimit)
            assertEquals(400, errorLimit)
        }
    }

    private fun LogConfig.snapshot() = listOf(maxMessageLength, infoLimit, warnLimit, errorLimit)
}
