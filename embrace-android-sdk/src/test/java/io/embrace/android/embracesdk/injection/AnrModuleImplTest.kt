package io.embrace.android.embracesdk.injection

import android.os.Looper
import io.embrace.android.embracesdk.anr.NoOpAnrService
import io.embrace.android.embracesdk.capture.monitor.NoOpResponsivenessMonitorService
import io.embrace.android.embracesdk.config.local.AutomaticDataCaptureLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.system.mockLooper
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class AnrModuleImplTest {

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockLooper()
    }

    @Test
    fun testDefaultImplementations() {
        val module = AnrModuleImpl(
            FakeInitModule(),
            FakeEssentialServiceModule(),
            WorkerThreadModuleImpl(FakeInitModule())
        )
        assertNotNull(module.anrService)
        assertNotNull(module.googleAnrTimestampRepository)
        assertNotNull(module.responsivenessMonitorService)
    }

    @Test
    fun testBehaviorDisabled() {
        val module = AnrModuleImpl(
            FakeInitModule(),
            FakeEssentialServiceModule(
                configService = createConfigServiceWithAnrDisabled()
            ),
            FakeWorkerThreadModule()
        )
        assertTrue(module.anrService is NoOpAnrService)
        assertTrue(module.responsivenessMonitorService is NoOpResponsivenessMonitorService)
        assertNotNull(module.googleAnrTimestampRepository)
    }

    private fun createConfigServiceWithAnrDisabled() = FakeConfigService(
        autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(localCfg = {
            LocalConfig(
                "",
                false,
                SdkLocalConfig(
                    automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                        anrServiceEnabled = false
                    )
                )
            )
        })
    )
}
