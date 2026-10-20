package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeApplication
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertSame
import org.junit.Test

internal class InstrumentationFactoriesTest {

    @Test
    fun `test default behavior`() {
        val initServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT)
        val configService = FakeConfigService()
        val destination = FakeTelemetryDestination()
        val application = fakeApplication()
        val context = fakeContext(application)
        val store = FakeKeyValueStore()
        val storageService = FakeStorageService()
        val args = createInstrumentationArgs(
            configService = configService,
            destination = destination,
            errorHandler = initServices.errorHandler,
            clock = initServices.clock,
            context = context,
            application = application,
            storeProvider = { store },
            serializer = initServices.jsonSerializer,
            uuidSource = initServices.uuidSource,
            ordinalStore = FakeOrdinalStore(),
            processIdentifier = "pid",
            processStateTracker = FakeProcessStateTracker(),
            navigationTrackingService = FakeNavigationTrackingService(),
            telemetryService = initServices.telemetryService,
            workerThreadPool = FakeWorkerThreadPool(
                fakeInitServices = initServices,
                testWorkers = listOf(Worker.Background.NonIoRegWorker),
            ),
            sessionPartTracker = FakeSessionPartTracker(),
            userSessionPropertiesService = FakeUserSessionPropertiesService(),
            userSessionIdsProvider = { null },
            activeSessionIdsProvider = { FakeSessionIdsProvider().getActiveSessionIds() },
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            eventBus = EventBus(initServices.errorHandler),
        )
        assertSame(configService, args.configService)
        assertSame(destination, args.destination)
        assertSame(store, args.store)
    }
}
