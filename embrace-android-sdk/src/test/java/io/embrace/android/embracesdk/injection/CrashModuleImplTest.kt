package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSessionModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.ndk.NoopNativeCrashService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CrashModuleImplTest {

    private val autoDataCaptureBehaviorWithNdkEnabled = fakeAutoDataCaptureBehavior(
        localCfg = { LocalConfig(appId = "xYxYx", ndkEnabled = true, sdkConfig = SdkLocalConfig()) }
    )

    @Test
    fun testDefaultImplementations() {
        val module = CrashModuleImpl(
            InitModuleImpl(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeNativeModule(),
            FakeSessionModule(),
            FakeAnrModule(),
            FakeAndroidServicesModule(),
            FakeCustomerLogModule(),
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashService)
        assertNotNull(module.automaticVerificationExceptionHandler)
        assertTrue(module.nativeCrashService is NoopNativeCrashService)
    }

    @Test
    fun `default config turns on v2 native crash service`() {
        val module = CrashModuleImpl(
            InitModuleImpl(),
            FakeStorageModule(),
            FakeEssentialServiceModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = autoDataCaptureBehaviorWithNdkEnabled
                )
            ),
            FakeNativeModule(),
            FakeSessionModule(),
            FakeAnrModule(),
            FakeAndroidServicesModule(),
            FakeCustomerLogModule(),
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashService)
        assertNotNull(module.automaticVerificationExceptionHandler)
        assertTrue(module.nativeCrashService is NativeCrashDataSource)
    }
}
