package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NativeCoreModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = createNativeCoreModule(
            initModule,
            createCoreModule(mockk(relaxed = true), initModule),
            FakePayloadSourceModule(),
            FakeWorkerThreadModule(),
            FakeConfigModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                        ndkEnabled = true
                    )
                )
            ),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeOpenTelemetryModule(),
            { null },
            { null },
            { null },
        )
        assertNotNull(module.sharedObjectLoader)
        assertNotNull(module.symbolService)
        assertNotNull(module.processor)
        assertNotNull(module.delegate)
        assertNotNull(module.nativeCrashHandlerInstaller)
    }
}
