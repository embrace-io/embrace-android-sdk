package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class SdkObservabilityModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = SdkObservabilityModuleImpl(
            FakeInitModule(),
            FakeEssentialServiceModule()
        )
        assertNotNull(module.internalErrorService)
        assertNotNull(module.reportingLoggerAction)
    }
}
