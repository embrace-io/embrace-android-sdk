package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
internal class SdkInitEnvironmentAttributesTest {

    private lateinit var context: Context

    private var fakeUptimeMs: Long = 45_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `thermal status is mapped to its name`() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        assertEquals("moderate", environmentAttributes()[SdkInitAttributeKeys.THERMAL_STATUS])
    }

    @Test
    fun `install and update recency computed from package info`() {
        val packageInfo = shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
        packageInfo.firstInstallTime = NOW_MS - 90_000L
        packageInfo.lastUpdateTime = NOW_MS - 30_000L
        val attributes = environmentAttributes()
        assertEquals("90", attributes[SdkInitAttributeKeys.SECONDS_SINCE_INSTALL])
        assertEquals("30", attributes[SdkInitAttributeKeys.SECONDS_SINCE_UPDATE])
    }

    @Test
    fun `unset package timestamps omit the recency attributes`() {
        val attributes = environmentAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.SECONDS_SINCE_INSTALL))
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.SECONDS_SINCE_UPDATE))
    }

    @Test
    fun `memory availability reported as whole percentage of total`() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().apply {
            availMem = 1_500_000_000L
            totalMem = 4_000_000_000L
            lowMemory = false
        }
        shadowOf(activityManager).setMemoryInfo(memoryInfo)
        val attributes = environmentAttributes()
        // 37.5% rounds to 38
        assertEquals("38", attributes[SdkInitAttributeKeys.MEM_AVAILABLE_PCT])
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.LOW_MEMORY))
    }

    @Test
    fun `seconds since boot reported from awake time, not wall time since boot`() {
        assertEquals("45", environmentAttributes()[SdkInitAttributeKeys.SECONDS_SINCE_BOOT])
    }

    @Test
    fun `low memory flag emitted only when the system reports it`() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().apply {
            availMem = 200_000_000L
            totalMem = 4_000_000_000L
            lowMemory = true
        }
        shadowOf(activityManager).setMemoryInfo(memoryInfo)
        assertEquals("true", environmentAttributes()[SdkInitAttributeKeys.LOW_MEMORY])
    }

    private fun environmentAttributes(): Map<String, String> = sdkInitEnvironmentAttributes(
        powerManagerProvider = { context.getSystemService(Context.POWER_SERVICE) as? PowerManager },
        activityManagerProvider = { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager },
        packageInfo = context.packageManager.getPackageInfo(context.packageName, 0),
        nowMs = NOW_MS,
        uptimeMs = { fakeUptimeMs },
    )

    private companion object {
        const val NOW_MS = 1_700_000_000_000L
    }
}
