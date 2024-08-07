package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.injection.CrashModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CrashModuleImplTest {

    private val autoDataCaptureBehaviorWithNdkEnabled = fakeAutoDataCaptureBehavior(
        localCfg = { LocalConfig(appId = "xYxYx", ndkEnabled = true, sdkConfig = SdkLocalConfig()) }
    )

    @Test
    fun testDefaultImplementations() {
        val androidServicesModule = FakeNativeModule()
        val module = CrashModuleImpl(
            FakeInitModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeAndroidServicesModule(),
            androidServicesModule.ndkService::getUnityCrashId,
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashDataSource)
    }

    @Test
    fun `default config turns on v2 native crash service`() {
        val androidServicesModule = FakeNativeModule()
        val module = CrashModuleImpl(
            FakeInitModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = autoDataCaptureBehaviorWithNdkEnabled
                )
            ),
            FakeAndroidServicesModule(),
            androidServicesModule.ndkService::getUnityCrashId,
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashDataSource)
    }
}
