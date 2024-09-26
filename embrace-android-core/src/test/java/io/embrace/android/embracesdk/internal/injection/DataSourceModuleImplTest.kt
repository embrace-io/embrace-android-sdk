package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class DataSourceModuleImplTest {

    @Test
    fun `test default behavior`() {
        val fakeInitModule = FakeInitModule()
        val module = DataSourceModuleImpl(
            fakeInitModule,
            FakeConfigService(),
            FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorkerName = Worker.Background.NonIoRegWorker
            ),
        )
        assertSame(module.dataCaptureOrchestrator, module.embraceFeatureRegistry)
        assertNotNull(module.dataCaptureOrchestrator)
    }
}
