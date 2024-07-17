package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogMessageBehaviorImplTest {

    private val remote = LogRemoteConfig(
        256,
        200,
        300,
        400
    )

    @Test
    fun testDefaults() {
        with(fakeLogMessageBehavior()) {
            assertEquals(128, getLogMessageMaximumAllowedLength())
            assertEquals(100, getInfoLogLimit())
            assertEquals(100, getWarnLogLimit())
            assertEquals(250, getErrorLogLimit())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(fakeLogMessageBehavior(remoteCfg = { remote })) {
            assertEquals(256, getLogMessageMaximumAllowedLength())
            assertEquals(200, getInfoLogLimit())
            assertEquals(300, getWarnLogLimit())
            assertEquals(400, getErrorLogLimit())
        }
    }
}
