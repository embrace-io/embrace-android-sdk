package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataCaptureEventConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().dataCaptureEvent
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).dataCaptureEvent
        assertTrue(defaults.internalExceptionCaptureEnabled)
        assertNull(defaults.disabledEventAndLogPatterns)
        assertEquals(defaults.internalExceptionCaptureEnabled, resolved.internalExceptionCaptureEnabled)
        assertEquals(defaults.disabledEventAndLogPatterns, resolved.disabledEventAndLogPatterns)
    }

    @Test
    fun `remote overrides default`() {
        val patterns = setOf("my_event", "my_log")
        val remote = RemoteConfig(
            internalExceptionCaptureEnabled = false,
            disabledEventAndLogPatterns = patterns,
        )
        with(resolveDataCaptureEvent(remote)) {
            assertFalse(internalExceptionCaptureEnabled)
            assertEquals(patterns, disabledEventAndLogPatterns)
        }
    }
}
