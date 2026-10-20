package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.FakeLogLimitingService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Test

internal class UserSessionOrchestrationFactoriesTest {

    private val openTelemetryModule = FakeOpenTelemetryModule(otelSdkMode = OtelSdkMode.REGULAR)
    private val initModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT)
    private val workerThreadPool = FakeWorkerThreadPool(fakeInitModule = initModule)
    private val configService = FakeConfigService(
        config = EmbraceConfig(persistence = { PersistenceConfig(multiFileEnabled = { true }) }),
    )
    private val coreModule = FakeCoreModule()
    private val essentialServiceModule = FakeEssentialServiceModule()
    private val payloadSourceModule = FakePayloadSourceModule()

    @Test
    fun `session parts are written when they can be read back`() {
        createSessionOrchestrator(createSessionPartReader(FakeDeliveryModule()))
        storeCompletedSpan()
        assertEquals(0, openTelemetryModule.spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `session parts are not written without a reader`() {
        createSessionOrchestrator(null)
        storeCompletedSpan()
        assertEquals(1, openTelemetryModule.spanRepository.completedOtelSpans().size)
    }

    private fun storeCompletedSpan() {
        openTelemetryModule.spanRepository.storeCompletedOtelSpans(listOf(Span(name = "completed-span")))
    }

    private fun createSessionPartReader(deliveryModule: DeliveryModule): SessionPartReader = createSessionPartReader(
        context = coreModule.context,
        clock = initModule.clock,
        errorHandler = initModule.errorHandler,
        configService = configService,
        sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
        intakeService = deliveryModule.intakeService,
        cachedLogEnvelopeStore = deliveryModule.cachedLogEnvelopeStore,
        nativeCrashServiceProvider = { null },
        restoreDecisionProvider = { null },
        processIdProvider = { openTelemetryModule.otelSdkConfig.processIdentifier },
    )

    private fun createSessionOrchestrator(sessionPartReader: SessionPartReader?) {
        createSessionOrchestrator(
            clock = initModule.clock,
            errorHandler = initModule.errorHandler,
            uuidSource = initModule.uuidSource,
            startupClassifier = initModule.startupClassifier,
            telemetryService = initModule.telemetryService,
            configService = configService,
            processStateTracker = essentialServiceModule.processStateTracker,
            sessionPartTracker = essentialServiceModule.sessionPartTracker,
            sessionIdsProvider = essentialServiceModule.sessionIdsProvider,
            userService = essentialServiceModule.userService,
            userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService,
            telemetryDestination = essentialServiceModule.telemetryDestination,
            experimentRecordsProvider = { null },
            currentSessionPartSpan = openTelemetryModule.currentSessionPartSpan,
            spanRepository = openTelemetryModule.spanRepository,
            processIdentifier = openTelemetryModule.otelSdkConfig.processIdentifier,
            sessionPartEnvelopeSource = payloadSourceModule.sessionPartEnvelopeSource,
            logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
            metadataService = payloadSourceModule.metadataService,
            resourceSource = payloadSourceModule.resourceSource,
            envelopeMetadataSource = payloadSourceModule.envelopeMetadataSource,
            payloadStore = null,
            payloadCachingService = null,
            instrumentationRegistry = InstrumentationRegistryImpl(initModule.errorHandler),
            startupDurationProvider = { null },
            appVersionStartupCounterProvider = { null },
            logLimitingService = FakeLogLimitingService(),
            ordinalStore = coreModule.ordinalStore,
            store = coreModule.store,
            backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
            sessionReadWorker = workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker),
            sessionPartReader = sessionPartReader,
        )
    }
}
