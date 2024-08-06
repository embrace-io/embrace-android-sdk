package io.embrace.android.embracesdk.ndk

import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.NativeModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class NativeModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val coreModule = FakeCoreModule()
        val module = NativeModuleImpl(
            FakeInitModule(),
            coreModule,
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            FakeAndroidServicesModule(),
            FakeWorkerThreadModule()
        )
        assertNotNull(module.ndkService)
        assertNull(module.nativeThreadSamplerService)
        assertNull(module.nativeThreadSamplerInstaller)
        assertNotNull(module.nativeAnrOtelMapper)
    }
}
