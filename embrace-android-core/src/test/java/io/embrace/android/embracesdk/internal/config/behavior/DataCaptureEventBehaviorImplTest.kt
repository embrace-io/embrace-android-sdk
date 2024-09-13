package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createDataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureEventBehaviorImplTest {

    private val remote = RemoteConfig(
        internalExceptionCaptureEnabled = false,
        disabledEventAndLogPatterns = setOf("my_event", "my_log"),
        eventLimits = mapOf("test" to 100)
    )

    @Test
    fun testDefaults() {
        with(createDataCaptureEventBehavior()) {
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
        with(createDataCaptureEventBehavior(remoteCfg = { remote })) {
            assertFalse(isInternalExceptionCaptureEnabled())
            assertFalse(isEventEnabled("my_event"))
            assertTrue(isEventEnabled("other_event"))
            assertFalse(isLogMessageEnabled("my_log"))
            assertTrue(isLogMessageEnabled("other_log"))
            assertEquals(100L, getEventLimits()["test"])
        }
    }
}
