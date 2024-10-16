package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CrashModuleImplTest {

    private val autoDataCaptureBehaviorWithNdkEnabled = FakeAutoDataCaptureBehavior(ndkEnabled = true)

    @Test
    fun testDefaultImplementations() {
        val nativeFeatureModule = FakeNativeFeatureModule()
        val module = createCrashModule(
            FakeInitModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            FakeAndroidServicesModule(),
            nativeFeatureModule.ndkService::unityCrashId,
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashDataSource)
    }

    @Test
    fun `default config turns on v2 native crash service`() {
        val nativeFeatureModule = FakeNativeFeatureModule()
        val module = createCrashModule(
            FakeInitModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = autoDataCaptureBehaviorWithNdkEnabled
                )
            ),
            FakeAndroidServicesModule(),
            nativeFeatureModule.ndkService::unityCrashId,
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashDataSource)
    }
}
