package io.embrace.android.embracesdk.injection

import android.os.Looper
import io.embrace.android.embracesdk.anr.NoOpAnrService
import io.embrace.android.embracesdk.config.local.AutomaticDataCaptureLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class AnrModuleImplTest {

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
    }

    @Test
    fun testDefaultImplementations() {
        val module = AnrModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeSystemServiceModule(),
            FakeEssentialServiceModule()
        )
        assertNotNull(module.anrService)
        assertNotNull(module.googleAnrTimestampRepository)
    }

    @Test
    fun testBehaviorDisabled() {
        val module = AnrModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeSystemServiceModule(),
            FakeEssentialServiceModule(
                configService = createConfigServiceWithAnrDisabled()
            )
        )
        assertTrue(module.anrService is NoOpAnrService)
        assertNotNull(module.googleAnrTimestampRepository)
    }

    private fun createConfigServiceWithAnrDisabled() = FakeConfigService(
        autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(localCfg = {
            LocalConfig(
                "", false,
                SdkLocalConfig(
                    automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                        anrServiceEnabled = false
                    )
                )
            )
        })
    )
}
