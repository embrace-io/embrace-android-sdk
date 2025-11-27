package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeVersionChecker
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
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
    private val initModule = FakeInitModule()

    @Before
    fun setUp() {
        coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule)
    }

    @Config(sdk = [Build.VERSION_CODES.O])
    @Test
    fun testVersionChecksNew() {
        coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule, FakeVersionChecker(true))
        assertNotNull(coreModule.storageManager)
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun testVersionChecksOld() {
        coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule, FakeVersionChecker(false))
        assertNull(coreModule.storageManager)
    }

    @Test
    fun testSystemServiceModuleDefault() {
        assertNotNull(coreModule.activityManager)
        assertNotNull(coreModule.windowManager)
        assertNotNull(coreModule.connectivityManager)
        assertNull(coreModule.storageManager)
    }
}
