package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NativeFeatureModuleImplTest {

    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var module: NativeFeatureModule

    @Before
    fun setUp() {
        fakeConfigService = FakeConfigService(
            autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                ndkEnabled = true
            )
        )
        module = createNativeFeatureModule()
    }

    @Test
    fun testDefaultImplementations() {
        fakeConfigService = FakeConfigService(
            autoDataCaptureBehavior = FakeAutoDataCaptureBehavior()
        )
        module = createNativeFeatureModule()
        assertNull(module.nativeCrashService)
    }

    @Test
    fun `create native crash handler installer`() {
        module = createNativeFeatureModule()
        assertNotNull(module.nativeCrashService)
    }

    @Test
    fun `create services when native crash capture is enabled`() {
        module = createNativeFeatureModule()
        assertNotNull(module.nativeCrashService)
    }

    private fun createNativeFeatureModule(): NativeFeatureModuleImpl {
        val application = ApplicationProvider.getApplicationContext<Application>()
        return NativeFeatureModuleImpl(
            FakeNativeCoreModule(),
            FakeInstrumentationArgs(
                application = application,
                configService = fakeConfigService
            )
        )
    }
}
