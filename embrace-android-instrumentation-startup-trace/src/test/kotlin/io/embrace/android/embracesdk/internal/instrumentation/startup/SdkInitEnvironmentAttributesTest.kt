package io.embrace.android.embracesdk.internal.instrumentation.startup

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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `thermal status is mapped to its name`() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        assertEquals("moderate", environmentAttributes()[THERMAL_STATUS_ATTR])
    }

    @Test
    fun `install and update recency computed from package info`() {
        val packageInfo = shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
        packageInfo.firstInstallTime = NOW_MS - 90_000L
        packageInfo.lastUpdateTime = NOW_MS - 30_000L
        val attributes = environmentAttributes()
        assertEquals("90", attributes[SECONDS_SINCE_INSTALL_ATTR])
        assertEquals("30", attributes[SECONDS_SINCE_UPDATE_ATTR])
    }

    @Test
    fun `unset package timestamps omit the recency attributes`() {
        val attributes = environmentAttributes()
        assertFalse(attributes.containsKey(SECONDS_SINCE_INSTALL_ATTR))
        assertFalse(attributes.containsKey(SECONDS_SINCE_UPDATE_ATTR))
    }

    private fun environmentAttributes(): Map<String, String> = sdkInitEnvironmentAttributes(
        powerManagerProvider = { context.getSystemService(Context.POWER_SERVICE) as? PowerManager },
        packageInfo = context.packageManager.getPackageInfo(context.packageName, 0),
        nowMs = NOW_MS,
    )

    private companion object {
        const val NOW_MS = 1_700_000_000_000L
    }
}
