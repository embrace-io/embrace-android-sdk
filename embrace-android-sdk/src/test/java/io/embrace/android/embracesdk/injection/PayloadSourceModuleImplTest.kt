package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PayloadSourceModuleImplTest {

    @Test
    fun `module default values`() {
        val initModule = FakeInitModule()
        val module = PayloadSourceModuleImpl(
            initModule,
            FakeEssentialServiceModule(),
            FakeNativeModule(),
            FakeOpenTelemetryModule(),
            FakeAnrModule(),
        )
        assertNotNull(module.sessionEnvelopeSource)
        assertNotNull(module.logEnvelopeSource)
    }
}
