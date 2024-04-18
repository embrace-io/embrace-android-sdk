package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSessionModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.ndk.NoopNativeCrashService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CrashModuleImplTest {

    private val autoDataCaptureBehaviorWithNdkEnabled = fakeAutoDataCaptureBehavior(
        localCfg = { LocalConfig(appId = "xYxYx", ndkEnabled = true, sdkConfig = SdkLocalConfig()) }
    )

    private val oTelBehaviorWithBetaFeatureEnabled = fakeOTelBehavior(
        remoteCfg = {
            RemoteConfig(
                oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
            )
        }
    )

    @Test
    fun testDefaultImplementations() {
        val module = CrashModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            FakeNativeModule(),
            FakeSessionModule(),
            FakeAnrModule(),
            FakeDataContainerModule(),
            FakeAndroidServicesModule(),
            FakeCustomerLogModule(),
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashService)
        assertNotNull(module.automaticVerificationExceptionHandler)
        assertTrue(module.nativeCrashService is NoopNativeCrashService)
    }

    @Test
    fun `NdkService used as NativeCrashService if NDK feature is on`() {
        val module = CrashModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = autoDataCaptureBehaviorWithNdkEnabled
                )
            ),
            FakeDeliveryModule(),
            FakeNativeModule(),
            FakeSessionModule(),
            FakeAnrModule(),
            FakeDataContainerModule(),
            FakeAndroidServicesModule(),
            FakeCustomerLogModule(),
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashService)
        assertNotNull(module.automaticVerificationExceptionHandler)
        assertTrue(module.nativeCrashService is NdkService)
    }

    @Test
    fun `beta feature flag turns on v2 native crash service`() {
        val module = CrashModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = autoDataCaptureBehaviorWithNdkEnabled,
                    oTelBehavior = oTelBehaviorWithBetaFeatureEnabled
                )
            ),
            FakeDeliveryModule(),
            FakeNativeModule(),
            FakeSessionModule(),
            FakeAnrModule(),
            FakeDataContainerModule(),
            FakeAndroidServicesModule(),
            FakeCustomerLogModule(),
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashService)
        assertNotNull(module.automaticVerificationExceptionHandler)
        assertTrue(module.nativeCrashService is NativeCrashDataSource)
    }
}
