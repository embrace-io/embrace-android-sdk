package io.embrace.android.embracesdk.injection

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import android.view.WindowManager
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class SystemServiceModuleImplTest {

    @Test
    fun testVersionChecks() {
        val ctx = mockk<Context>(relaxed = true) {
            every { getSystemService("storagestats") } returns mockk<StorageStatsManager>()
        }
        val old = SystemServiceModuleImpl(
            FakeCoreModule(context = ctx),
            FakeVersionChecker(false)
        )
        assertNull(old.storageManager)

        val new = SystemServiceModuleImpl(
            FakeCoreModule(context = ctx),
            FakeVersionChecker(true)
        )
        assertNotNull(new.storageManager)
    }

    @Test
    fun testSystemServiceModuleDefault() {
        val ctx = mockk<Context>(relaxed = true) {
            every { getSystemService("activity") } returns mockk<ActivityManager>()
            every { getSystemService("power") } returns mockk<PowerManager>()
            every { getSystemService("window") } returns mockk<WindowManager>()
            every { getSystemService("connectivity") } returns mockk<ConnectivityManager>()
        }
        val module = SystemServiceModuleImpl(FakeCoreModule(context = ctx))

        assertNotNull(module.activityManager)
        assertNotNull(module.powerManager)
        assertNotNull(module.windowManager)
        assertNotNull(module.connectivityManager)
        assertNull(module.storageManager)
    }

    @Test
    fun testSystemServiceModuleException() {
        val ctx = mockk<Context>()
        val module = SystemServiceModuleImpl(FakeCoreModule(context = ctx))

        assertNull(module.activityManager)
        assertNull(module.powerManager)
        assertNull(module.storageManager)
        assertNull(module.windowManager)
        assertNull(module.connectivityManager)
    }
}
