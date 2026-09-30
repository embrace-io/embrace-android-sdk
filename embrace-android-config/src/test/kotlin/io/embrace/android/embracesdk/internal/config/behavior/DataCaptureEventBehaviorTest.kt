package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.DataCaptureEventConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveDataCaptureEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureEventBehaviorTest {

    @Test
    fun `all events and logs enabled by default`() {
        with(DataCaptureEventBehavior(DataCaptureEventConfig())) {
            assertTrue(isEventEnabled("my_event"))
            assertTrue(isEventEnabled("other_event"))
            assertTrue(isLogMessageEnabled("my_log"))
            assertTrue(isLogMessageEnabled("other_log"))
        }
    }

    @Test
    fun `disabled patterns block matching events and logs`() {
        val config = resolveDataCaptureEvent(RemoteConfig(disabledEventAndLogPatterns = setOf("my_event", "my_log")))
        with(DataCaptureEventBehavior(config)) {
            assertFalse(isEventEnabled("my_event"))
            assertTrue(isEventEnabled("other_event"))
            assertFalse(isLogMessageEnabled("my_log"))
            assertTrue(isLogMessageEnabled("other_log"))
        }
    }
}
