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
        val fakeInitModule = FakeInitModule()
        val module = DataSourceModuleImpl(
            fakeInitModule,
            FakeOpenTelemetryModule(),
            FakeEssentialServiceModule(),
            FakeSystemServiceModule(),
            FakeAndroidServicesModule(),
            FakeWorkerThreadModule(fakeInitModule = fakeInitModule, name = WorkerName.BACKGROUND_REGISTRATION)
        )
        assertNotNull(module.getDataSources())
        assertNotNull(module.breadcrumbDataSource)
        assertNotNull(module.tapDataSource)
        assertNotNull(module.viewDataSource)
        assertNotNull(module.webViewUrlDataSource)
        assertNotNull(module.pushNotificationDataSource)
        assertNotNull(module.sessionPropertiesDataSource)
        assertEquals(6, module.getDataSources().size)
    }
}
