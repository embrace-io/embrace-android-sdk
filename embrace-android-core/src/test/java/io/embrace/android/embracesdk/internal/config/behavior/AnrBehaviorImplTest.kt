package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
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
        nativeThreadAnrSamplingFactor = 2,
        nativeThreadAnrSamplingUnwinder = "libunwindstack",
        pctNativeThreadAnrSamplingEnabled = 100.0f,
        nativeThreadAnrSamplingOffsetEnabled = false,
        ignoreNativeThreadAnrSamplingAllowlist = false,
        nativeThreadAnrSamplingAllowlist = listOf(
            AllowedNdkSampleMethod(
                "MyFoo",
                "bar"
            )
        ),
        monitorThreadPriority = 3
    )

    @Test
    fun testDefaults() {
        with(createAnrBehavior()) {
            assertEquals(100L, getSamplingIntervalMs())
            assertTrue(isNativeThreadAnrSamplingOffsetEnabled())
            assertTrue(isNativeThreadAnrSamplingAllowlistIgnored())
            assertEquals(80, getMaxStacktracesPerInterval())
            assertEquals(200, getStacktraceFrameLimit())
            assertEquals(5, getMaxAnrIntervalsPerSession())
            assertEquals(1000, getMinDuration())
            assertFalse(isUnityAnrCaptureEnabled())
            assertTrue(isAnrCaptureEnabled())
            assertEquals(Unwinder.LIBUNWIND, getNativeThreadAnrSamplingUnwinder())
            assertEquals(500, getNativeThreadAnrSamplingIntervalMs())
            assertEquals(5, getNativeThreadAnrSamplingFactor())

            val expected = AllowedNdkSampleMethod("UnityPlayer", "pauseUnity")
            val observed = getNativeThreadAnrSamplingAllowlist().single()
            assertEquals(expected.clz, observed.clz)
            assertEquals(expected.method, observed.method)
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createAnrBehavior(remoteCfg = RemoteConfig(anrConfig = remote))) {
            assertEquals(200L, getSamplingIntervalMs())
            assertFalse(isNativeThreadAnrSamplingOffsetEnabled())
            assertFalse(isNativeThreadAnrSamplingAllowlistIgnored())
            assertEquals(120, getMaxStacktracesPerInterval())
            assertEquals(300, getStacktraceFrameLimit())
            assertEquals(10, getMaxAnrIntervalsPerSession())
            assertEquals(2000, getMinDuration())
            assertTrue(isUnityAnrCaptureEnabled())
            assertFalse(isAnrCaptureEnabled())
            assertEquals(Unwinder.LIBUNWINDSTACK, getNativeThreadAnrSamplingUnwinder())
            assertEquals(400, getNativeThreadAnrSamplingIntervalMs())
            assertEquals(2, getNativeThreadAnrSamplingFactor())

            val expected = AllowedNdkSampleMethod("MyFoo", "bar")
            val observed = getNativeThreadAnrSamplingAllowlist().single()
            assertEquals(expected.clz, observed.clz)
            assertEquals(expected.method, observed.method)
        }
    }
}
