package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertSame
import org.junit.Test

internal class InstrumentationFactoriesTest {

    @Test
    fun `test default behavior`() {
        val initModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT)
        val configService = FakeConfigService()
        val essentialServiceModule = FakeEssentialServiceModule()
        val coreModule = FakeCoreModule()
        val storageService = FakeStorageService()
        val args = createInstrumentationArgs(
            configService = configService,
            destination = essentialServiceModule.telemetryDestination,
            errorHandler = initModule.errorHandler,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            storeProvider = { coreModule.store },
            serializer = initModule.jsonSerializer,
            uuidSource = initModule.uuidSource,
            ordinalStore = coreModule.ordinalStore,
            processIdentifier = "pid",
            processStateTracker = essentialServiceModule.processStateTracker,
            navigationTrackingService = essentialServiceModule.navigationTrackingService,
            telemetryService = initModule.telemetryService,
            workerThreadPool = FakeWorkerThreadPool(
                fakeInitModule = initModule,
                testWorkers = listOf(Worker.Background.NonIoRegWorker),
            ),
            sessionPartTracker = essentialServiceModule.sessionPartTracker,
            userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService,
            userSessionIdsProvider = { null },
            activeSessionIdsProvider = { FakeSessionIdsProvider().getActiveSessionIds() },
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            eventBus = coreModule.eventBus,
        )
        assertSame(configService, args.configService)
        assertSame(essentialServiceModule.telemetryDestination, args.destination)
        assertSame(coreModule.store, args.store)
    }
}
