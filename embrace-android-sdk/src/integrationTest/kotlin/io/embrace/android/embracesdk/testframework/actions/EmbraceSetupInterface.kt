package io.embrace.android.embracesdk.testframework.actions

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
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createAnrModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import io.embrace.android.embracesdk.internal.injection.createEssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.createNativeCoreModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface @JvmOverloads constructor(
    currentTimeMs: Long = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    workerToFake: Worker.Background? = null,
    priorityWorkerToFake: Worker.Priority? = null,
    anrMonitoringThread: Thread? = null,
    var useMockWebServer: Boolean = true,
    val processIdentifier: String = "integration-test-process",
    val overriddenInitModule: FakeInitModule = FakeInitModule(
        clock = FakeClock(currentTime = currentTimeMs),
        logger = FakeEmbLogger(ignoredErrors = mutableListOf(InternalErrorType.PROCESS_STATE_CALLBACK_FAIL)),
        processIdentifierProvider = { processIdentifier }
    ),
    val overriddenOpenTelemetryModule: OpenTelemetryModule = overriddenInitModule.openTelemetryModule,
    val overriddenCoreModule: FakeCoreModule = FakeCoreModule(),
    val overriddenAndroidServicesModule: AndroidServicesModule = createAndroidServicesModule(
        initModule = overriddenInitModule,
        coreModule = overriddenCoreModule
    ),
    var cacheStorageServiceProvider: Provider<PayloadStorageService>? = null,
    var payloadStorageServiceProvider: Provider<PayloadStorageService>? = null,
    val networkConnectivityService: FakeNetworkConnectivityService = FakeNetworkConnectivityService(),
    var jniDelegate: FakeJniDelegate = FakeJniDelegate(),
    var symbols: Map<String, String> = mapOf("libfoo.so" to "symbol_content"),
    val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.INITIALIZED),
) {
    private val workerThreadModule: WorkerThreadModule = initWorkerThreadModule(
        fakeInitModule = overriddenInitModule,
        workerToFake = workerToFake,
        priorityWorkerToFake = priorityWorkerToFake,
        anrMonitoringThread = anrMonitoringThread
    )

    private val anrModule: AnrModule = if (anrMonitoringThread != null) {
        createAnrModule(
            overriddenInitModule,
            FakeConfigService(),
            workerThreadModule
        )
    } else {
        FakeAnrModule()
    }

    fun createBootstrapper(
        instrumentedConfig: FakeInstrumentedConfig,
        deliveryTracer: DeliveryTracer,
    ): ModuleInitBootstrapper = ModuleInitBootstrapper(
        initModule = overriddenInitModule.apply {
            this.instrumentedConfig = instrumentedConfig
        },
        openTelemetryModule = overriddenInitModule.openTelemetryModule,
        coreModuleSupplier = { _, _ -> overriddenCoreModule },
        workerThreadModuleSupplier = { workerThreadModule },
        androidServicesModuleSupplier = { _, _ -> overriddenAndroidServicesModule },
        essentialServiceModuleSupplier = { initModule, configModule, openTelemetryModule, coreModule, workerThreadModule, systemServiceModule, androidServicesModule, storageModule, _, _ ->
            createEssentialServiceModule(
                initModule,
                configModule,
                openTelemetryModule,
                coreModule,
                workerThreadModule,
                systemServiceModule,
                androidServicesModule,
                storageModule,
                { lifecycleOwner }
            ) { networkConnectivityService }
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
                configModule,
                initModule,
                otelModule,
                workerThreadModule,
                coreModule,
                storageModule,
                essentialServiceModule,
                androidServicesModule,
                payloadStorageServiceProvider = payloadStorageServiceProvider,
                cacheStorageServiceProvider = cacheStorageServiceProvider,
                requestExecutionServiceProvider = requestExecutionServiceProvider,
                deliveryServiceProvider = deliveryServiceProvider,
                deliveryTracer = deliveryTracer
            )
        },
        anrModuleSupplier = { _, _, _ -> anrModule },
        nativeCoreModuleSupplier = { initModule, coreModule, payloadSourceModule, workerThreadModule, configModule, storageModule, essentialServiceModule, openTelemetryModule, _, _, _ ->
            createNativeCoreModule(
                initModule,
                coreModule,
                payloadSourceModule,
                workerThreadModule,
                configModule,
                storageModule,
                essentialServiceModule,
                openTelemetryModule,
                { jniDelegate },
                ::FakeSharedObjectLoader,
                { FakeSymbolService(symbols) }
            )
        },
    )


    /**
     * Setup a fake dead session on disk
     */
    fun setupCachedDataFromNativeCrash(
        storageService: FakePayloadStorageService,
        crashData: StoredNativeCrashData,
    ) {
        if (crashData.sessionMetadata != null && crashData.sessionEnvelope != null) {
            storageService.addPayload(crashData.sessionMetadata, crashData.sessionEnvelope)
        }
        if (crashData.cachedCrashEnvelopeMetadata != null && crashData.cachedCrashEnvelope != null) {
            storageService.addPayload(crashData.cachedCrashEnvelopeMetadata, crashData.cachedCrashEnvelope)
        }
        cacheStorageServiceProvider = { storageService }
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
        jniDelegate.addCrashRaw(key, json)
    }

    fun getClock(): FakeClock = checkNotNull(overriddenInitModule.getFakeClock())

    fun getFakedWorkerExecutor(): BlockingScheduledExecutorService =
        (workerThreadModule as FakeWorkerThreadModule).executor

    fun getFakedPriorityWorkerExecutor(): BlockingScheduledExecutorService =
        (workerThreadModule as FakeWorkerThreadModule).priorityWorkerExecutor

    fun getBlockedThreadDetector(): BlockedThreadDetector = anrModule.blockedThreadDetector

    private companion object {
        fun initWorkerThreadModule(
            fakeInitModule: FakeInitModule,
            workerToFake: Worker.Background?,
            priorityWorkerToFake: Worker.Priority?,
            anrMonitoringThread: Thread?,
        ): WorkerThreadModule =
            if (workerToFake == null) {
                createWorkerThreadModule()
            } else {
                FakeWorkerThreadModule(
                    fakeInitModule = fakeInitModule,
                    testWorker = workerToFake,
                    testPriorityWorker = priorityWorkerToFake,
                    anrMonitoringThread = anrMonitoringThread
                )
            }
    }
}
