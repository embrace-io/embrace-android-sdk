package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PayloadSourceModuleImplTest {

    @Test
    fun `module default values`() {
        val initModule = FakeInitModule()
        val module = PayloadSourceModuleImpl(
            initModule,
            FakeCoreModule(),
            FakeWorkerThreadModule(),
            FakeSystemServiceModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            ::FakeNativeCoreModule,
            ::FakeNativeFeatureModule,
            FakeOpenTelemetryModule(),
            FakeAnrModule(),
        )
        assertTrue(module.metadataService is EmbraceMetadataService)
        assertNotNull(module.sessionEnvelopeSource)
        assertNotNull(module.logEnvelopeSource)
        assertTrue(module.deviceArchitecture is DeviceArchitectureImpl)
    }
}
