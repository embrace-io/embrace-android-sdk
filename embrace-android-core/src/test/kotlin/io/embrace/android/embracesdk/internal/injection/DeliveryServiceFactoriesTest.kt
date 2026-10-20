package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeliveryServiceFactoriesTest {

    private val initServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT)
    private val context = Application()
    private val workerThreadPool = FakeWorkerThreadPool()

    @Test
    fun testFactories() {
        assertNotNull(
            createPayloadStorageService(
                location = StorageLocation.PAYLOAD,
                context = context,
                worker = workerThreadPool.priorityWorker(Worker.Priority.DataPersistenceWorker),
                processIdProvider = { "pid" },
                errorHandler = initServices.errorHandler,
                clock = initServices.clock,
                deliveryTracer = null,
            ),
        )
        val payloadStore = createPayloadStore(
            intakeService = FakeIntakeService(),
            clock = initServices.clock,
            processIdProvider = { "pid" },
            uuidSource = initServices.uuidSource,
            sessionIdsProvider = FakeSessionIdsProvider()::getActiveSessionIds,
        )
        assertNotNull(
            createPayloadCachingService(
                worker = workerThreadPool.backgroundWorker(Worker.Background.PeriodicCacheWorker),
                errorHandler = initServices.errorHandler,
                periodicCacheIntervalMs = 2000,
                clock = initServices.clock,
                sessionIdsProvider = FakeSessionIdsProvider(),
                payloadStore = payloadStore,
                deliveryTracer = null,
            ),
        )
        assertNotNull(
            createCachedLogEnvelopeStore(
                context = context,
                worker = workerThreadPool.priorityWorker(Worker.Priority.DataPersistenceWorker),
                errorHandler = initServices.errorHandler,
                serializer = initServices.jsonSerializer,
                clock = initServices.clock,
            ),
        )
    }
}
