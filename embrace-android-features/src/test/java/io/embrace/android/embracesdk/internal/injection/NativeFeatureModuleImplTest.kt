package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NativeFeatureModuleImplTest {

    private lateinit var fakeConfigModule: FakeConfigModule
    private lateinit var fakeStorageService: FakeStorageService
    private lateinit var fakeEssentialServiceModule: FakeEssentialServiceModule

    private lateinit var module: NativeFeatureModuleImpl

    @Before
    fun setUp() {
        fakeConfigModule = FakeConfigModule()
        fakeStorageService = FakeStorageService()
        fakeEssentialServiceModule = FakeEssentialServiceModule()
        module = createNativeFeatureModule(fakeConfigModule)
    }

    @Test
    fun testDefaultImplementations() {
        assertNull(module.nativeThreadSamplerService)
        assertNull(module.nativeThreadSamplerInstaller)
        assertNotNull(module.nativeAnrOtelMapper)
        assertNull(module.nativeCrashService)
        assertNull(module.nativeCrashHandlerInstaller)
    }

    @Test
    fun `do not create native crash handler installer when create native crash id throws`() {
        fakeStorageService.shouldThrow = true
        fakeConfigModule = FakeConfigModule(
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    ndkEnabled = true
                )
            )
        )

        module = createNativeFeatureModule(fakeConfigModule)

        assertNull(module.nativeCrashHandlerInstaller)
    }

    @Test
    fun `do not create native crash handler installer when active session id is null`() {
        // given active session id is null
        fakeConfigModule = FakeConfigModule(
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    ndkEnabled = true
                )
            )
        )
        fakeEssentialServiceModule = FakeEssentialServiceModule(sessionIdTracker = FakeSessionIdTracker())

        module = createNativeFeatureModule(fakeConfigModule)

        assertNull(module.nativeCrashHandlerInstaller)
    }

    @Test
    fun `create native crash handler installer when everything is fine`() {
        fakeConfigModule = FakeConfigModule(
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    ndkEnabled = true
                )
            )
        )
        fakeEssentialServiceModule = FakeEssentialServiceModule(
            sessionIdTracker = FakeSessionIdTracker().apply {
                setActiveSession("sessionId", true)
            }
        )

        module = createNativeFeatureModule(fakeConfigModule)

        assertNotNull(module.nativeCrashHandlerInstaller)
    }

    @Test
    fun `create services when native crash capture is enabled`() {
        fakeConfigModule = FakeConfigModule(
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    ndkEnabled = true
                )
            )
        )

        module = createNativeFeatureModule(fakeConfigModule)

        assertNotNull(module.nativeCrashService)
        assertNotNull(module.nativeThreadSamplerService)
        assertNotNull(module.nativeThreadSamplerInstaller)
    }

    private fun createNativeFeatureModule(fakeConfigModule: FakeConfigModule): NativeFeatureModuleImpl {
        val initModule = FakeInitModule()
        return NativeFeatureModuleImpl(
            initModule,
            FakeStorageModule(storageService = fakeStorageService),
            fakeEssentialServiceModule,
            fakeConfigModule,
            FakePayloadSourceModule(),
            FakeAndroidServicesModule(),
            FakeWorkerThreadModule(),
            FakeNativeCoreModule()
        )
    }
}
