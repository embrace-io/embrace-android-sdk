package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeFeatureRegistry
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FeatureModuleImplTest {

    @Test
    fun testFeatureModule() {
        val registry = FakeFeatureRegistry()
        val module = FeatureModuleImpl(
            featureRegistry = registry,
            coreModule = createCoreModule(mockk(relaxed = true), FakeEmbLogger()),
            initModule = FakeInitModule(),
            otelModule = FakeOpenTelemetryModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            systemServiceModule = FakeSystemServiceModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            anrModule = FakeAnrModule(),
            logWriter = FakeLogWriter(),
            configService = FakeConfigService()
        )
        assertNotNull(module.breadcrumbDataSource)
        assertNotNull(module.viewDataSource)
        assertNotNull(module.pushNotificationDataSource)
        assertNotNull(module.tapDataSource)
        assertNotNull(module.rnActionDataSource)
        assertNotNull(module.sessionPropertiesDataSource)
        assertNotNull(module.webViewDataSource)
        assertNotNull(module.lowPowerDataSource)
        assertNotNull(module.applicationExitInfoDataSource)
        assertNotNull(module.internalErrorDataSource)

        val expectedFeatures = 14
        assertEquals(expectedFeatures, registry.states.size)
        module.registerFeatures()
        assertEquals(expectedFeatures + 1, registry.states.size)
    }
}
