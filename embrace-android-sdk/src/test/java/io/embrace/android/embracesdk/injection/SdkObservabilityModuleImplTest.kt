package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class SdkObservabilityModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = SdkObservabilityModuleImpl(
            InitModuleImpl(),
            FakeEssentialServiceModule()
        )
        assertNotNull(module.internalErrorService)
        assertNotNull(module.reportingLoggerAction)
    }
}
