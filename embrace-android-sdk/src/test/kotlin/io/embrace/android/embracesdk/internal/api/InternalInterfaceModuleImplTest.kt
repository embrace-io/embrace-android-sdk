package io.embrace.android.embracesdk.internal.api

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class InternalInterfaceModuleImplTest {

    @Test
    fun testModule() {
        val initModule = FakeInitModule()
        val module: InternalInterfaceModule = InternalInterfaceModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeConfigModule(),
            FakePayloadSourceModule(),
            FakeInstrumentationModule(ApplicationProvider.getApplicationContext()),
            EmbraceImpl(),
            ModuleInitBootstrapper(FakeInitModule())
        )

        assertNotNull(module.flutterInternalInterface)
        assertNotNull(module.unityInternalInterface)
        assertNotNull(module.reactNativeInternalInterface)
        assertNotNull(module.embraceInternalInterface)
    }
}
