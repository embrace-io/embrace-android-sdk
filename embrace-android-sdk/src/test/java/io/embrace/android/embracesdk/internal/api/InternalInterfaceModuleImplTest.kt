package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.injection.FakeCrashModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeMomentsModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class InternalInterfaceModuleImplTest {

    @Test
    fun testModule() {
        val initModule = FakeInitModule()
        val module: InternalInterfaceModule = InternalInterfaceModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeEssentialServiceModule(),
            FakeLogModule(),
            FakeMomentsModule(),
            EmbraceImpl(),
            FakeCrashModule()
        )

        assertNotNull(module.flutterInternalInterface)
        assertNotNull(module.unityInternalInterface)
        assertNotNull(module.reactNativeInternalInterface)
        assertNotNull(module.embraceInternalInterface)
    }
}
