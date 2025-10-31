package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeFeatureRegistry
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
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
            destination = FakeTelemetryDestination(),
            configService = FakeConfigService()
        )
        assertNotNull(module.breadcrumbDataSource)
        assertNotNull(module.rnActionDataSource)
        assertNotNull(module.internalErrorDataSource)
    }
}
