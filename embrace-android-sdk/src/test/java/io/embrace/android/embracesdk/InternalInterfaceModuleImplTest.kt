package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCrashModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class InternalInterfaceModuleImplTest {

    @Test
    fun testModule() {
        val module: InternalInterfaceModule = InternalInterfaceModuleImpl(
            FakeInitModule(),
            FakeCoreModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            EmbraceImpl(),
            FakeCrashModule()
        )

        assertNotNull(module.flutterInternalInterface)
        assertNotNull(module.unityInternalInterface)
        assertNotNull(module.reactNativeInternalInterface)
        assertNotNull(module.embraceInternalInterface)
        assertNotNull(module.sdkApi)
    }
}
