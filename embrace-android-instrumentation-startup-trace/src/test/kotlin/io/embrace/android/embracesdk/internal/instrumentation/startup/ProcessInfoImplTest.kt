package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbStartupLaunchReasonValues
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class ProcessInfoImplTest {

    private val fakeDeviceStartTime = 100_000L
    private lateinit var processInfo: ProcessInfo

    @Before
    fun setUp() {
        processInfo = ProcessInfoImpl(fakeDeviceStartTime, BuildVersionChecker)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify start time in T`() {
        val startRequestElapsedTime = Process.getStartRequestedElapsedRealtime()
        val startRequestedEpochTime = checkNotNull(processInfo.startRequestedTimeMs())
        assertEquals(startRequestElapsedTime, startRequestedEpochTime - fakeDeviceStartTime)
    }

    @Config(sdk = [Build.VERSION_CODES.N])
    @Test
    fun `verify start time in N`() {
        val startElapsedTime = Process.getStartElapsedRealtime()
        val startRequestedEpochTime = checkNotNull(processInfo.startRequestedTimeMs())
        assertEquals(startElapsedTime, startRequestedEpochTime - fakeDeviceStartTime)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify start time in L`() {
        assertNull(processInfo.startRequestedTimeMs())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify launch reason in V`() {
        val activityManager = activityManagerWith(startInfo(ApplicationStartInfo.START_REASON_PUSH, startedBeforeUs))
        assertEquals(EmbStartupLaunchReasonValues.PUSH, processInfo(activityManager).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify launch reason read from records the platform did not populate a pid for in V`() {
        val record = startInfo(ApplicationStartInfo.START_REASON_LAUNCHER, startedBeforeUs)
        every { record.pid } returns 0
        assertEquals(EmbStartupLaunchReasonValues.LAUNCHER, processInfo(activityManagerWith(record)).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify starts logged after our process was requested are not reported as ours in V`() {
        val activityManager = activityManagerWith(
            startInfo(ApplicationStartInfo.START_REASON_BROADCAST, startedAfterUs),
            startInfo(ApplicationStartInfo.START_REASON_LAUNCHER, startedBeforeUs),
        )
        assertEquals(EmbStartupLaunchReasonValues.LAUNCHER, processInfo(activityManager).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify no launch reason when every record postdates our process being requested in V`() {
        val activityManager =
            activityManagerWith(startInfo(ApplicationStartInfo.START_REASON_BROADCAST, startedAfterUs))
        assertNull(processInfo(activityManager).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify no launch reason when a record cannot be placed in time in V`() {
        val record = startInfo(ApplicationStartInfo.START_REASON_LAUNCHER, startedBeforeUs)
        every { record.startupTimestamps } returns emptyMap()
        assertNull(processInfo(activityManagerWith(record)).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify no launch reason when no start records exist in V`() {
        assertNull(processInfo(activityManagerWith()).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify no launch reason for an unrecognised platform reason in V`() {
        assertNull(processInfo(activityManagerWith(startInfo(Int.MAX_VALUE, startedBeforeUs))).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify no launch reason when the platform call fails in V`() {
        val activityManager = mockk<ActivityManager> {
            every { getHistoricalProcessStartReasons(any()) } throws IllegalStateException("dead")
        }
        assertNull(processInfo(activityManager).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify no launch reason without an ActivityManager in V`() {
        assertNull(processInfo(activityManager = null).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify no launch reason in U`() {
        val activityManager =
            activityManagerWith(startInfo(ApplicationStartInfo.START_REASON_LAUNCHER, startedBeforeUs))
        assertNull(processInfo(activityManager).launchReason())
    }

    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    @Test
    fun `verify the platform is only consulted once however often the reason is read in V`() {
        val activityManager = activityManagerWith(startInfo(ApplicationStartInfo.START_REASON_JOB, startedBeforeUs))
        val processInfo = processInfo(activityManager)

        processInfo.prefetchLaunchReason()
        assertEquals(EmbStartupLaunchReasonValues.JOB, processInfo.launchReason())
        assertEquals(EmbStartupLaunchReasonValues.JOB, processInfo.launchReason())

        verify(exactly = 1) { activityManager.getHistoricalProcessStartReasons(any()) }
    }

    private fun processInfo(activityManager: ActivityManager?) =
        ProcessInfoImpl(fakeDeviceStartTime, BuildVersionChecker, activityManager)

    private fun activityManagerWith(vararg records: ApplicationStartInfo) = mockk<ActivityManager> {
        every { getHistoricalProcessStartReasons(any()) } returns records.toList()
    }

    /**
     * Records are matched against [Process.getStartRequestedUptimeMillis], so anchor the fixtures to whatever this process reports
     * rather than to a literal.
     */
    private val startedBeforeUs: Long
        get() = (Process.getStartRequestedUptimeMillis() - 10L) * NANOS_PER_MS

    private val startedAfterUs: Long
        get() = (Process.getStartRequestedUptimeMillis() + 10L) * NANOS_PER_MS

    private fun startInfo(reason: Int, launchUptimeNs: Long) = mockk<ApplicationStartInfo> {
        every { this@mockk.reason } returns reason
        every { startupTimestamps } returns mapOf(ApplicationStartInfo.START_TIMESTAMP_LAUNCH to launchUptimeNs)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
