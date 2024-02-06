package io.embrace.android.embracesdk.ndk

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class NativeModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = NativeModuleImpl(
            FakeCoreModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            FakeAndroidServicesModule(),
            fakeEmbraceSessionProperties(),
            FakeWorkerThreadModule()
        )
        assertNotNull(module.ndkService)
        assertNull(module.nativeThreadSamplerService)
        assertNull(module.nativeThreadSamplerInstaller)
    }
}
