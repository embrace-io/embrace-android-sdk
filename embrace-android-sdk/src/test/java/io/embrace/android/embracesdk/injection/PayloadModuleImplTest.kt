package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PayloadModuleImplTest {

    @Test
    fun `module default values`() {
        val initModule = InitModuleImpl()
        val coreModule = FakeCoreModule()
        val module = PayloadModuleImpl(
            initModule,
            coreModule,
            FakeAndroidServicesModule(),
            FakeEssentialServiceModule(),
            FakeSystemServiceModule(),
            WorkerThreadModuleImpl(initModule),
            FakeNativeModule(),
            FakeOpenTelemetryModule(),
            FakeAnrModule(),
            ::FakeSessionPropertiesService,
            ::FakeWebViewService
        )
        assertNotNull(module.sessionEnvelopeSource)
        assertNotNull(module.logEnvelopeSource)
    }
}
