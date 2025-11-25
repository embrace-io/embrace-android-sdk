package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class InstrumentationModuleImplTest {

    @Test
    fun `test default behavior`() {
        val fakeInitModule = FakeInitModule()
        val module = InstrumentationModuleImpl(
            fakeInitModule,
            FakeOpenTelemetryModule(),
            FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorker = Worker.Background.NonIoRegWorker
            ),
            FakeConfigModule(),
            FakeEssentialServiceModule(),
            FakeCoreModule(),
        )
        assertSame(module.instrumentationRegistry, module.instrumentationRegistry)
        assertNotNull(module.instrumentationRegistry)
        assertNotNull(module.instrumentationArgs)
    }
}
