package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class SystemServiceModuleImplTest {

    private lateinit var coreModule: CoreModule

    @Before
    fun setUp() {
        coreModule = createCoreModule(RuntimeEnvironment.getApplication())
    }

    @Config(sdk = [Build.VERSION_CODES.O])
    @Test
    fun testVersionChecksNew() {
        val new = SystemServiceModuleImpl(
            createCoreModule(RuntimeEnvironment.getApplication()),
            FakeVersionChecker(true)
        )
        assertNotNull(new.storageManager)
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun testVersionChecksOld() {
        val old = SystemServiceModuleImpl(coreModule, FakeVersionChecker(false))
        assertNull(old.storageManager)
    }

    @Test
    fun testSystemServiceModuleDefault() {
        val module = SystemServiceModuleImpl(coreModule)
        assertNotNull(module.activityManager)
        assertNotNull(module.powerManager)
        assertNotNull(module.windowManager)
        assertNotNull(module.connectivityManager)
        assertNull(module.storageManager)
    }
}
