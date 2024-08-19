package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.injection.createCrashModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CrashModuleImplTest {

    private val autoDataCaptureBehaviorWithNdkEnabled = fakeAutoDataCaptureBehavior(
        localCfg = { LocalConfig(appId = "xYxYx", ndkEnabled = true, sdkConfig = SdkLocalConfig()) }
    )

    @Test
    fun testDefaultImplementations() {
        val nativeFeatureModule = FakeNativeFeatureModule()
        val module = createCrashModule(
            FakeInitModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            FakeAndroidServicesModule(),
            nativeFeatureModule.ndkService::getUnityCrashId,
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
            nativeFeatureModule.ndkService::getUnityCrashId,
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashDataSource)
    }
}
