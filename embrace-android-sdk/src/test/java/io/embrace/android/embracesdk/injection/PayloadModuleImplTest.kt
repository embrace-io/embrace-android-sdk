package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSdkObservabilityModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PayloadModuleImplTest {

    @Test
    fun `module default values`() {
        val initModule = InitModuleImpl()
        val module = PayloadModuleImpl(
            FakeEssentialServiceModule(),
            FakeCoreModule(),
            FakeAndroidServicesModule(),
            FakeSystemServiceModule(),
            workerThreadModule = WorkerThreadModuleImpl(initModule),
            FakeNativeModule(),
            FakeOpenTelemetryModule(),
            FakeSdkObservabilityModule()
        )
        assertNotNull(module.sessionEnvelopeSource)
        assertNotNull(module.logEnvelopeSource)
    }
}
