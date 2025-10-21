package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AnrBehaviorImplTest {

    private val remote = AnrRemoteConfig(
        pctEnabled = 0,
        sampleIntervalMs = 200,
        maxStacktracesPerInterval = 120,
        stacktraceFrameLimit = 300,
        anrPerSession = 10,
        minDuration = 2000,
        monitorThreadPriority = 3
    )

    @Test
    fun testDefaults() {
        with(createAnrBehavior()) {
            assertEquals(100L, getSamplingIntervalMs())
            assertEquals(80, getMaxStacktracesPerInterval())
            assertEquals(200, getStacktraceFrameLimit())
            assertEquals(5, getMaxAnrIntervalsPerSession())
            assertEquals(1000, getMinDuration())
            assertTrue(isAnrCaptureEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createAnrBehavior(remoteCfg = RemoteConfig(anrConfig = remote))) {
            assertEquals(200L, getSamplingIntervalMs())
            assertEquals(120, getMaxStacktracesPerInterval())
            assertEquals(300, getStacktraceFrameLimit())
            assertEquals(10, getMaxAnrIntervalsPerSession())
            assertEquals(2000, getMinDuration())
            assertFalse(isAnrCaptureEnabled())
        }
    }
}
