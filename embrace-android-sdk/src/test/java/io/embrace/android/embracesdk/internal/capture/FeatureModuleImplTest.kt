package io.embrace.android.embracesdk.internal.capture

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FeatureModuleImplTest {

    @Test
    fun testFeatureModule() {
        val module = FeatureModuleImpl(
            coreModule = FakeCoreModule(),
            initModule = FakeInitModule(),
            otelModule = FakeOpenTelemetryModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            logWriter = FakeLogWriter(),
            configService = FakeConfigService()
        )
        assertNotNull(module.breadcrumbDataSource)
        assertNotNull(module.viewDataSource)
        assertNotNull(module.memoryWarningDataSource)
        assertNotNull(module.pushNotificationDataSource)
        assertNotNull(module.tapDataSource)
        assertNotNull(module.rnActionDataSource)
        assertNotNull(module.sessionPropertiesDataSource)
        assertNotNull(module.webViewDataSource)
        assertNotNull(module.lowPowerDataSource)
        assertNotNull(module.applicationExitInfoDataSource)
        assertNotNull(module.internalErrorDataSource)
    }
}
