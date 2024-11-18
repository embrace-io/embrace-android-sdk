package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class PayloadSourceModuleImplTest {

    @Test
    fun `module default values`() {
        val initModule = FakeInitModule()
        val module = PayloadSourceModuleImpl(
            initModule,
            CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule),
            FakeWorkerThreadModule(),
            FakeSystemServiceModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            ::HashMap,
            FakeOpenTelemetryModule(),
            ::FakeOtelPayloadMapper,
            FakeDeliveryModule(),
        )
        assertTrue(module.metadataService is EmbraceMetadataService)
        assertNotNull(module.sessionEnvelopeSource)
        assertNotNull(module.logEnvelopeSource)
        assertTrue(module.deviceArchitecture is DeviceArchitectureImpl)
        assertNotNull(module.payloadResurrectionService)
    }

    @Test
    fun `payload resurrection service is created when v2 delivery layer is off`() {
        val initModule = FakeInitModule()
        val module = PayloadSourceModuleImpl(
            initModule,
            CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule),
            FakeWorkerThreadModule(),
            FakeSystemServiceModule(),
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(
                configService = FakeConfigService(
                    autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                        v2StorageEnabled = false
                    )
                )
            ),
            ::HashMap,
            FakeOpenTelemetryModule(),
            ::FakeOtelPayloadMapper,
            FakeDeliveryModule(),
        )
        assertNull(module.payloadResurrectionService)
    }
}
