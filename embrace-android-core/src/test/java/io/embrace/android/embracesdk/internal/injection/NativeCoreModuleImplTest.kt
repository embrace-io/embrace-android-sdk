package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NativeCoreModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val module = NativeCoreModuleImpl(
            FakeConfigModule(),
            FakeWorkerThreadModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeInstrumentationArgs(
                ctx,
                FakeConfigService(
                    autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                        ndkEnabled = true
                    )
                )
            ),
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
