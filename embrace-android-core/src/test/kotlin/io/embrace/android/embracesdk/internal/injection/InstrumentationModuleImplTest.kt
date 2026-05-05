package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeActiveSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeStorageService
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
            initModule = fakeInitModule,
            openTelemetryModule = FakeOpenTelemetryModule(),
            workerThreadModule = FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorkers = listOf(Worker.Background.NonIoRegWorker)
            ),
            configService = FakeConfigService(),
            essentialServiceModule = FakeEssentialServiceModule(),
            coreModule = FakeCoreModule(),
            storageService = FakeStorageService(),
            userSessionIdProvider = { null },
            activeSessionIdsProvider = { FakeActiveSessionIdsProvider().getActiveSessionIds() },
        )
        assertSame(module.instrumentationRegistry, module.instrumentationRegistry)
        assertNotNull(module.instrumentationRegistry)
        assertNotNull(module.instrumentationArgs)
    }
}
