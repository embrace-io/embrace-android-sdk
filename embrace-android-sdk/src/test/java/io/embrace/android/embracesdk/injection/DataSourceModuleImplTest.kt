package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DataSourceModuleImplTest {

    @Test
    fun `test default behavior`() {
        val module = DataSourceModuleImpl(
            FakeEssentialServiceModule(),
            FakeInitModule(),
            FakeOpenTelemetryModule(),
            FakeSystemServiceModule(),
            FakeAndroidServicesModule(),
            FakeWorkerThreadModule(FakeInitModule(), WorkerName.BACKGROUND_REGISTRATION)
        )
        assertNotNull(module.getDataSources())
        assertNotNull(module.customBreadcrumbDataSource)
        assertNotNull(module.fragmentBreadcrumbDataSource)
        assertNotNull(module.memoryWarningDataSource)
        assertEquals(3, module.getDataSources().size)
    }
}
