package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ThreadBlockageConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().threadBlockage
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).threadBlockage
        with(defaults) {
            assertTrue(captureEnabled)
            assertEquals(100L, sampleIntervalMs)
            assertEquals(80, maxStacktracesPerInterval)
            assertEquals(200, stacktraceFrameLimit)
            assertEquals(5, maxIntervalsPerSession)
            assertEquals(1000, minDurationMs)
        }
        assertEquals(defaults.captureEnabled, resolved.captureEnabled)
        assertEquals(defaults.sampleIntervalMs, resolved.sampleIntervalMs)
        assertEquals(defaults.maxStacktracesPerInterval, resolved.maxStacktracesPerInterval)
        assertEquals(defaults.stacktraceFrameLimit, resolved.stacktraceFrameLimit)
        assertEquals(defaults.maxIntervalsPerSession, resolved.maxIntervalsPerSession)
        assertEquals(defaults.minDurationMs, resolved.minDurationMs)
    }

    @Test
    fun `remote values override defaults`() {
        val remote = ThreadBlockageRemoteConfig(
            pctEnabled = 0,
            sampleIntervalMs = 200,
            maxStacktracesPerInterval = 120,
            stacktraceFrameLimit = 300,
            intervalsPerSession = 10,
            minDuration = 2000,
        )
        with(resolve(remote, unreadBucket)) {
            assertFalse(captureEnabled)
            assertEquals(200L, sampleIntervalMs)
            assertEquals(120, maxStacktracesPerInterval)
            assertEquals(300, stacktraceFrameLimit)
            assertEquals(10, maxIntervalsPerSession)
            assertEquals(2000, minDurationMs)
        }
    }

    @Test
    fun `partial rollout reads bucket`() {
        val remote = ThreadBlockageRemoteConfig(pctEnabled = 50)
        assertTrue(resolve(remote, lazy { 49f }).captureEnabled)
        assertFalse(resolve(remote, lazy { 51f }).captureEnabled)
    }

    private fun resolve(remote: ThreadBlockageRemoteConfig, bucket: Lazy<Float>) =
        resolveThreadBlockage(RemoteConfig(threadBlockageRemoteConfig = remote), bucket)
}
