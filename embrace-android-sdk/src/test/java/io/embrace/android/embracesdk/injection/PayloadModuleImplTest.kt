package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSdkObservabilityModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PayloadModuleImplTest {

    @Test
    fun `module default values`() {
        val module = PayloadModuleImpl(
            FakeEssentialServiceModule(),
            FakeNativeModule(),
            FakeOpenTelemetryModule(),
            FakeSdkObservabilityModule()
        )
        assertNotNull(module.sessionEnvelopeSource)
        assertNotNull(module.logEnvelopeSource)
    }
}
