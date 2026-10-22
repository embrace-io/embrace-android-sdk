package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeCachedLogEnvelopeStore
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeLogLimitingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeApplication
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Test

internal class UserSessionOrchestrationFactoriesTest {

    private val spanRepository = SpanRepository(FakeErrorHandler())
    private val initServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT)
    private val workerThreadPool = FakeWorkerThreadPool(fakeInitServices = initServices)
    private val configService = FakeConfigService(
        config = EmbraceConfig(persistence = { PersistenceConfig(multiFileEnabled = { true }) }),
    )
    private val application = fakeApplication()
    private val context = fakeContext(application)
    private val store = FakeKeyValueStore()
    private val destination = FakeTelemetryDestination()

    @Test
    fun `session parts are written when they can be read back`() {
        createSessionOrchestrator(createSessionPartReader())
        storeCompletedSpan()
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `session parts are not written without a reader`() {
        createSessionOrchestrator(null)
        storeCompletedSpan()
        assertEquals(1, spanRepository.completedOtelSpans().size)
    }

    private fun storeCompletedSpan() {
        spanRepository.storeCompletedOtelSpans(listOf(Span(name = "completed-span")))
    }

    private fun createSessionPartReader(): SessionPartReader = createSessionPartReader(
        context = context,
        clock = initServices.clock,
        errorHandler = initServices.errorHandler,
        configService = configService,
        sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
        intakeService = FakeIntakeService(),
        cachedLogEnvelopeStore = FakeCachedLogEnvelopeStore(),
        nativeCrashServiceProvider = { null },
        restoreDecisionProvider = { null },
        processIdProvider = { "pid" },
    )

    private fun createSessionOrchestrator(sessionPartReader: SessionPartReader?) {
        createSessionOrchestrator(
            clock = initServices.clock,
            errorHandler = initServices.errorHandler,
            uuidSource = initServices.uuidSource,
            startupClassifier = initServices.startupClassifier,
            telemetryService = initServices.telemetryService,
            configService = configService,
            processStateTracker = FakeProcessStateTracker(),
            sessionPartTracker = FakeSessionPartTracker(),
            sessionIdsProvider = FakeSessionIdsProvider(),
            userService = FakeUserService(),
            userSessionPropertiesService = FakeUserSessionPropertiesService(),
            telemetryDestination = destination,
            experimentRecordsProvider = { null },
            currentSessionPartSpan = FakeCurrentSessionPartSpan(),
            spanRepository = spanRepository,
            processIdentifier = "pid",
            sessionPartEnvelopeSource = FakeSessionPartEnvelopeSource(),
            logEnvelopeSource = FakeLogEnvelopeSource(),
            metadataService = FakeMetadataService(),
            resourceSource = FakeEnvelopeResourceSource(),
            envelopeMetadataSource = FakeEnvelopeMetadataSource(),
            payloadStore = null,
            payloadCachingService = null,
            instrumentationRegistry = InstrumentationRegistryImpl(initServices.errorHandler),
            startupDurationProvider = { null },
            appVersionStartupCounterProvider = { null },
            logLimitingService = FakeLogLimitingService(),
            ordinalStore = FakeOrdinalStore(),
            store = store,
            backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
            sessionReadWorker = workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker),
            sessionPartReader = sessionPartReader,
        )
    }
}
