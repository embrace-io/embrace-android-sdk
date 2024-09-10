package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.config.local.AnrLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

internal class AnrBehaviorImplTest {

    private val local = AnrLocalConfig(
        captureGoogle = true,
        captureUnityThread = true
    )

    private val remote = AnrRemoteConfig(
        pctEnabled = 0,
        pctAnrProcessErrorsEnabled = 100,
        pctBgEnabled = 100,
        sampleIntervalMs = 200,
        anrProcessErrorsIntervalMs = 300,
        anrProcessErrorsDelayMs = 2000,
        anrProcessErrorsSchedulerExtraTimeAllowance = 50000,
        maxStacktracesPerInterval = 120,
        stacktraceFrameLimit = 300,
        anrPerSession = 10,
        mainThreadOnly = false,
        minThreadPriority = 2,
        minDuration = 2000,
        allowList = listOf("test"),
        blockList = listOf("test2"),
        nativeThreadAnrSamplingFactor = 2,
        nativeThreadAnrSamplingUnwinder = "libunwindstack",
        pctNativeThreadAnrSamplingEnabled = 100.0f,
        nativeThreadAnrSamplingOffsetEnabled = false,
        pctIdleHandlerEnabled = 100.0f,
        pctStrictModeListenerEnabled = 100.0f,
        strictModeViolationLimit = 209,
        ignoreNativeThreadAnrSamplingAllowlist = false,
        nativeThreadAnrSamplingAllowlist = listOf(
            AllowedNdkSampleMethod(
                "MyFoo",
                "bar"
            )
        ),
        googlePctEnabled = 0,
        monitorThreadPriority = 3
    )

    @Test
    fun testDefaults() {
        with(createAnrBehavior()) {
            assertFalse(isGoogleAnrCaptureEnabled())
            assertEquals(100L, getSamplingIntervalMs())
            assertTrue(shouldCaptureMainThreadOnly())
            assertEquals(25, getStrictModeViolationLimit())
            assertTrue(isNativeThreadAnrSamplingOffsetEnabled())
            assertTrue(isNativeThreadAnrSamplingAllowlistIgnored())
            assertEquals(1000, getAnrProcessErrorsIntervalMs())
            assertEquals(5000, getAnrProcessErrorsDelayMs())
            assertEquals(30000, getAnrProcessErrorsSchedulerExtraTimeAllowanceMs())
            assertEquals(80, getMaxStacktracesPerInterval())
            assertEquals(100, getStacktraceFrameLimit())
            assertEquals(5, getMaxAnrIntervalsPerSession())
            assertEquals(0, getMinThreadPriority())
            assertEquals(1000, getMinDuration())
            assertEquals(0, getMonitorThreadPriority())
            assertFalse(isIdleHandlerEnabled())
            assertFalse(isStrictModeListenerEnabled())
            assertFalse(isBgAnrCaptureEnabled())
            assertFalse(isNativeThreadAnrSamplingEnabled())
            assertFalse(isAnrProcessErrorsCaptureEnabled())
            assertTrue(isAnrCaptureEnabled())
            assertEquals(Unwinder.LIBUNWIND, getNativeThreadAnrSamplingUnwinder())
            assertEquals(500, getNativeThreadAnrSamplingIntervalMs())
            assertEquals(5, getNativeThreadAnrSamplingFactor())
            assertEquals(emptyList<Pattern>(), allowPatternList)
            assertEquals(emptyList<Pattern>(), blockPatternList)

            val expected = AllowedNdkSampleMethod("UnityPlayer", "pauseUnity")
            val observed = getNativeThreadAnrSamplingAllowlist().single()
            assertEquals(expected.clz, observed.clz)
            assertEquals(expected.method, observed.method)
        }
    }

    @Test
    fun testLocalOnly() {
        with(createAnrBehavior(localCfg = { local })) {
            assertTrue(isGoogleAnrCaptureEnabled())
            assertTrue(isNativeThreadAnrSamplingEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createAnrBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(isGoogleAnrCaptureEnabled())
            assertEquals(200L, getSamplingIntervalMs())
            assertFalse(shouldCaptureMainThreadOnly())
            assertEquals(209, getStrictModeViolationLimit())
            assertFalse(isNativeThreadAnrSamplingOffsetEnabled())
            assertFalse(isNativeThreadAnrSamplingAllowlistIgnored())
            assertEquals(300, getAnrProcessErrorsIntervalMs())
            assertEquals(2000, getAnrProcessErrorsDelayMs())
            assertEquals(50000, getAnrProcessErrorsSchedulerExtraTimeAllowanceMs())
            assertEquals(120, getMaxStacktracesPerInterval())
            assertEquals(300, getStacktraceFrameLimit())
            assertEquals(10, getMaxAnrIntervalsPerSession())
            assertEquals(2, getMinThreadPriority())
            assertEquals(2000, getMinDuration())
            assertEquals(3, getMonitorThreadPriority())
            assertTrue(isIdleHandlerEnabled())
            assertTrue(isStrictModeListenerEnabled())
            assertTrue(isBgAnrCaptureEnabled())
            assertTrue(isNativeThreadAnrSamplingEnabled())
            assertTrue(isAnrProcessErrorsCaptureEnabled())
            assertFalse(isAnrCaptureEnabled())
            assertEquals(Unwinder.LIBUNWINDSTACK, getNativeThreadAnrSamplingUnwinder())
            assertEquals(400, getNativeThreadAnrSamplingIntervalMs())
            assertEquals(2, getNativeThreadAnrSamplingFactor())
            assertEquals("test", allowPatternList.single().pattern())
            assertEquals("test2", blockPatternList.single().pattern())

            val expected = AllowedNdkSampleMethod("MyFoo", "bar")
            val observed = getNativeThreadAnrSamplingAllowlist().single()
            assertEquals(expected.clz, observed.clz)
            assertEquals(expected.method, observed.method)
        }
    }
}
