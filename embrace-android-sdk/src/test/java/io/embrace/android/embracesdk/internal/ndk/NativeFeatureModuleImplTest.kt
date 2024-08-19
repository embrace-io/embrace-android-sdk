package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.NativeFeatureModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NativeFeatureModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val coreModule = FakeCoreModule()
        val module = NativeFeatureModuleImpl(
            FakeInitModule(),
            coreModule,
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            FakePayloadSourceModule(),
            FakeDeliveryModule(),
            FakeAndroidServicesModule(),
            FakeWorkerThreadModule(),
            FakeNativeCoreModule()
        )
        assertNotNull(module.ndkService)
        assertNull(module.nativeThreadSamplerService)
        assertNull(module.nativeThreadSamplerInstaller)
        assertNotNull(module.nativeAnrOtelMapper)
        assertTrue(module.nativeCrashService is NoopNativeCrashService)
    }
}
