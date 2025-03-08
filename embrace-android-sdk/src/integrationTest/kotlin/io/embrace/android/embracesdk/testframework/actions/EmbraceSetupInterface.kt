package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.FakeSymbolService
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createAnrModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import io.embrace.android.embracesdk.internal.injection.createEssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.createNativeCoreModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface @JvmOverloads constructor(
    workerToFake: Worker.Background? = null,
    anrMonitoringThread: Thread? = null,
    fakeStorageLayer: Boolean = false,
    var useMockWebServer: Boolean = true,
    val ignoredInternalErrors: List<InternalErrorType> = listOf(InternalErrorType.APP_LAUNCH_TRACE_FAIL),
) {
    private val processIdentifier: String = Uuid.getEmbUuid()

    val fakeNetworkConnectivityService = FakeNetworkConnectivityService()
    val fakeJniDelegate = FakeJniDelegate()
    val fakeSymbolService = FakeSymbolService()
    val fakeLifecycleOwner: TestLifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.INITIALIZED)
    val fakePayloadStorageService = if (fakeStorageLayer) {
        FakePayloadStorageService(processIdentifier)
    } else {
        null
    }
    val fakeCacheStorageService = if (fakeStorageLayer) {
        FakePayloadStorageService(processIdentifier)
    } else {
        null
    }

    private val fakeInitModule: FakeInitModule = FakeInitModule(
        clock = FakeClock(currentTime = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS),
        logger = FakeEmbLogger(ignoredErrors = ignoredInternalErrors),
        processIdentifier = processIdentifier
    )

    private val workerThreadModule: WorkerThreadModule = initWorkerThreadModule(
        fakeInitModule = fakeInitModule,
        workerToFake = workerToFake,
        anrMonitoringThread = anrMonitoringThread
    )

    private val anrModule: AnrModule = if (anrMonitoringThread != null) {
        createAnrModule(
            fakeInitModule,
            FakeConfigService(),
            workerThreadModule
        )
    } else {
        FakeAnrModule()
    }

    private val coreModule: CoreModule = FakeCoreModule()

    private val androidServicesModule: AndroidServicesModule = createAndroidServicesModule(
        initModule = fakeInitModule,
        coreModule = coreModule
    )

    fun createBootstrapper(
        instrumentedConfig: FakeInstrumentedConfig,
        deliveryTracer: DeliveryTracer,
    ): ModuleInitBootstrapper = ModuleInitBootstrapper(
        initModule = fakeInitModule.apply {
            this.instrumentedConfig = instrumentedConfig
        },
        openTelemetryModule = fakeInitModule.openTelemetryModule,
        coreModuleSupplier = { _, _ -> coreModule },
        workerThreadModuleSupplier = { workerThreadModule },
        androidServicesModuleSupplier = { _, _ -> androidServicesModule },
        essentialServiceModuleSupplier = { initModule, configModule, openTelemetryModule, coreModule, workerThreadModule, systemServiceModule, androidServicesModule, storageModule, _, _ ->
            createEssentialServiceModule(
                initModule = initModule,
                configModule = configModule,
                openTelemetryModule = openTelemetryModule,
                coreModule = coreModule,
                workerThreadModule = workerThreadModule,
                systemServiceModule = systemServiceModule,
                androidServicesModule = androidServicesModule,
                storageModule = storageModule,
                lifecycleOwnerProvider = { fakeLifecycleOwner },
                networkConnectivityServiceProvider = { fakeNetworkConnectivityService }
            )
        },
        deliveryModuleSupplier = { configModule, initModule, otelModule, workerThreadModule, coreModule, storageModule, essentialServiceModule, androidServicesModule, _, _, _, _, _ ->
            val requestExecutionServiceProvider: Provider<RequestExecutionService>? = when {
                useMockWebServer -> null
                else -> ::FakeRequestExecutionService
            }
            val deliveryServiceProvider: Provider<DeliveryService>? = when {
                useMockWebServer -> null
                else -> ::FakeDeliveryService
            }
            createDeliveryModule(
                configModule = configModule,
                initModule = initModule,
                otelModule = otelModule,
                workerThreadModule = workerThreadModule,
                coreModule = coreModule,
                storageModule = storageModule,
                essentialServiceModule = essentialServiceModule,
                androidServicesModule = androidServicesModule,
                payloadStorageServiceProvider = fakePayloadStorageService?.let { { it } },
                cacheStorageServiceProvider = fakeCacheStorageService?.let { { it } },
                requestExecutionServiceProvider = requestExecutionServiceProvider,
                deliveryServiceProvider = deliveryServiceProvider,
                deliveryTracer = deliveryTracer
            )
        },
        anrModuleSupplier = { _, _, _ -> anrModule },
        nativeCoreModuleSupplier = { initModule, coreModule, payloadSourceModule, workerThreadModule, configModule, storageModule, essentialServiceModule, openTelemetryModule, _, _, _ ->
            createNativeCoreModule(
                initModule = initModule,
                coreModule = coreModule,
                payloadSourceModule = payloadSourceModule,
                workerThreadModule = workerThreadModule,
                configModule = configModule,
                storageModule = storageModule,
                essentialServiceModule = essentialServiceModule,
                otelModule = openTelemetryModule,
                delegateProvider = { fakeJniDelegate },
                sharedObjectLoaderProvider = ::FakeSharedObjectLoader,
                symbolServiceProvider = { fakeSymbolService }
            )
        },
    )

    /**
     * Setup a fake dead session on disk
     */
    fun setupCachedDataFromNativeCrash(crashData: StoredNativeCrashData) {
        if (fakeCacheStorageService != null) {
            if (crashData.sessionMetadata != null && crashData.sessionEnvelope != null) {
                fakeCacheStorageService.addPayload(crashData.sessionMetadata, crashData.sessionEnvelope)
            }
            if (crashData.cachedCrashEnvelopeMetadata != null && crashData.cachedCrashEnvelope != null) {
                fakeCacheStorageService.addPayload(crashData.cachedCrashEnvelopeMetadata, crashData.cachedCrashEnvelope)
            }
        }
    }

    /**
     * Setup a fake native crash on disk
     */
    fun setupFakeNativeCrash(
        serializer: PlatformSerializer,
        crashData: StoredNativeCrashData,
    ) {
        crashData.getCrashFile().createNewFile()
        val key = crashData.getCrashFile().absolutePath
        val json = serializer.toJson(crashData.nativeCrash, NativeCrashData::class.java)
        fakeJniDelegate.addCrashRaw(key, json)
    }

    fun getClock(): FakeClock = checkNotNull(fakeInitModule.getFakeClock())

    fun getSpanSink(): SpanSink = fakeInitModule.openTelemetryModule.spanSink

    fun getCurrentSessionSpan(): CurrentSessionSpan = fakeInitModule.openTelemetryModule.currentSessionSpan

    fun getEmbLogger(): FakeEmbLogger = fakeInitModule.logger as FakeEmbLogger

    fun getContext(): Context = coreModule.context

    fun getFakedWorkerExecutor(): BlockingScheduledExecutorService = (workerThreadModule as FakeWorkerThreadModule).executor

    fun getBlockedThreadDetector(): BlockedThreadDetector = anrModule.blockedThreadDetector

    fun getPreferencesService(): PreferencesService = androidServicesModule.preferencesService

    private companion object {
        fun initWorkerThreadModule(
            fakeInitModule: FakeInitModule,
            workerToFake: Worker.Background?,
            anrMonitoringThread: Thread?,
        ): WorkerThreadModule =
            if (workerToFake == null) {
                createWorkerThreadModule()
            } else {
                FakeWorkerThreadModule(
                    fakeInitModule = fakeInitModule,
                    testWorker = workerToFake,
                    anrMonitoringThread = anrMonitoringThread
                )
            }
    }
}
