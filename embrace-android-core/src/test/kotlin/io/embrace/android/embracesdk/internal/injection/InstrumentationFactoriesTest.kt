package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveredAeiStore
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureRuleStore
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeApplication
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.fakes.testSdkStorage
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

internal class InstrumentationFactoriesTest {

    @Test
    fun `test default behavior`() {
        val initServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT)
        val configService = FakeConfigService()
        val destination = FakeTelemetryDestination()
        val application = fakeApplication()
        val context = fakeContext(application)
        val deliveredAeiStore = FakeDeliveredAeiStore()
        val networkCaptureRuleStore = FakeNetworkCaptureRuleStore()
        val sdkStorage = testSdkStorage(File("unused"))
        val args = createInstrumentationArgs(
            configService = configService,
            destination = destination,
            errorHandler = initServices.errorHandler,
            clock = initServices.clock,
            context = context,
            application = application,
            deliveredAeiStoreProvider = { deliveredAeiStore },
            networkCaptureRuleStoreProvider = { networkCaptureRuleStore },
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
            crashMarkerFileProvider = { File("unused") },
            sdkStorage = sdkStorage,
            eventBus = EventBus(initServices.errorHandler),
        )
        assertSame(configService, args.configService)
        assertSame(destination, args.destination)
        assertSame(sdkStorage, args.sdkStorage)

        assertSame(deliveredAeiStore, args.deliveredAeiStore)
        assertSame(networkCaptureRuleStore, args.networkCaptureRuleStore)

        // a service that isn't found is returned as null each time rather than throwing
        assertNull(args.systemService<Any>("no-such-service"))
        assertNull(args.systemService<Any>("no-such-service"))
    }
}
