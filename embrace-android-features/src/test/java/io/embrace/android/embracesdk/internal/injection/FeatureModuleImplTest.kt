package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureRegistry
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FeatureModuleImplTest {

    @Test
    fun testFeatureModule() {
        val registry = FakeFeatureRegistry()
        val initModule = FakeInitModule()
        val module = FeatureModuleImpl(
            featureRegistry = registry,
            initModule = initModule,
            otelModule = FakeOpenTelemetryModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            logWriter = FakeLogWriter(),
            configService = FakeConfigService()
        )
        assertNotNull(module.breadcrumbDataSource)
        assertNotNull(module.viewDataSource)
        assertNotNull(module.pushNotificationDataSource)
        assertNotNull(module.rnActionDataSource)
        assertNotNull(module.applicationExitInfoDataSource)
        assertNotNull(module.internalErrorDataSource)
    }
}
