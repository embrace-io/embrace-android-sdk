package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCrashModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class InternalInterfaceModuleImplTest {

    @Test
    fun testModule() {
        val initModule = FakeInitModule()
        val module: InternalInterfaceModule = InternalInterfaceModuleImpl(
            initModule,
            initModule.openTelemetryModule,
            FakeCoreModule(),
            FakeEssentialServiceModule(),
            FakeCustomerLogModule(),
            FakeDataContainerModule(),
            EmbraceImpl(),
            FakeCrashModule()
        )

        assertNotNull(module.flutterInternalInterface)
        assertNotNull(module.unityInternalInterface)
        assertNotNull(module.reactNativeInternalInterface)
        assertNotNull(module.embraceInternalInterface)
    }
}
