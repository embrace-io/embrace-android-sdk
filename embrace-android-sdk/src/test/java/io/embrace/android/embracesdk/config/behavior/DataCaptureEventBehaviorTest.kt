package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureEventBehaviorTest {

    private val remote = RemoteConfig(
        internalExceptionCaptureEnabled = false,
        disabledEventAndLogPatterns = setOf("my_event", "my_log"),
        eventLimits = mapOf("test" to 100)
    )

    @Test
    fun testDefaults() {
        with(fakeDataCaptureEventBehavior()) {
            assertTrue(isInternalExceptionCaptureEnabled())
            assertTrue(isEventEnabled("my_event"))
            assertTrue(isEventEnabled("other_event"))
            assertTrue(isLogMessageEnabled("my_log"))
            assertTrue(isLogMessageEnabled("other_log"))
            assertEquals(mapOf<String, Long>(), getEventLimits())
        }
    }

    @Test
    fun testRemoteOnly() {
        with(fakeDataCaptureEventBehavior(remoteCfg = { remote })) {
            assertFalse(isInternalExceptionCaptureEnabled())
            assertFalse(isEventEnabled("my_event"))
            assertTrue(isEventEnabled("other_event"))
            assertFalse(isLogMessageEnabled("my_log"))
            assertTrue(isLogMessageEnabled("other_log"))
            assertEquals(100L, getEventLimits()["test"])
        }
    }
}
