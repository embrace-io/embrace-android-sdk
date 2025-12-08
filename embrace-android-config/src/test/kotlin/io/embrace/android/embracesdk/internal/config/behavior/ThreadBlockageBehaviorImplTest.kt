package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createThreadBlockageBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ThreadBlockageBehaviorImplTest {

    private val remote = ThreadBlockageRemoteConfig(
        pctEnabled = 0,
        sampleIntervalMs = 200,
        maxStacktracesPerInterval = 120,
        stacktraceFrameLimit = 300,
        intervalsPerSession = 10,
        minDuration = 2000,
        monitorThreadPriority = 3
    )

    @Test
    fun testDefaults() {
        with(createThreadBlockageBehavior()) {
            assertEquals(100L, getSamplingIntervalMs())
            assertEquals(80, getMaxStacktracesPerInterval())
            assertEquals(200, getStacktraceFrameLimit())
            assertEquals(5, getMaxIntervalsPerSession())
            assertEquals(1000, getMinDuration())
            assertTrue(isThreadBlockageCaptureEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createThreadBlockageBehavior(remoteCfg = RemoteConfig(threadBlockageRemoteConfig = remote))) {
            assertEquals(200L, getSamplingIntervalMs())
            assertEquals(120, getMaxStacktracesPerInterval())
            assertEquals(300, getStacktraceFrameLimit())
            assertEquals(10, getMaxIntervalsPerSession())
            assertEquals(2000, getMinDuration())
            assertFalse(isThreadBlockageCaptureEnabled())
        }
    }
}
