package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.Worker
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class DataSourceModuleImplTest {

    @Test
    fun `test default behavior`() {
        val fakeInitModule = FakeInitModule()
        val module = DataSourceModuleImpl(
            fakeInitModule,
            FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorker = Worker.Background.NonIoRegWorker
            ),
            FakeConfigModule(),
            FakeEssentialServiceModule(),
            FakeAndroidServicesModule(),
            CoreModuleImpl(mockk<Application>(relaxed = true), fakeInitModule),
        )
        assertSame(module.dataCaptureOrchestrator, module.embraceFeatureRegistry)
        assertNotNull(module.dataCaptureOrchestrator)
        assertNotNull(module.instrumentationContext)
    }
}
