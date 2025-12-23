package io.embrace.android.embracesdk.testframework.actions

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.config.CpuAbi
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.injection.DeliveryModuleImpl
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jniDelegateTestOverride
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.sharedObjectLoaderTestOverride
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.ExperimentalApi

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface(
    workerToFake: Worker.Background? = null,
    private val threadBlockageWatchdogThread: Thread? = null,
    fakeStorageLayer: Boolean = false,
    val ignoredInternalErrors: List<InternalErrorType> = emptyList(),
    val fakeClock: FakeClock = FakeClock(currentTime = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS),
) {
    private val processIdentifier: String = Uuid.getEmbUuid()

    val fakeNetworkConnectivityService = FakeNetworkConnectivityService()
    val fakeJniDelegate = FakeJniDelegate().also {
        jniDelegateTestOverride = it
    }
    val fakeSharedObjectLoader = FakeSharedObjectLoader().also {
        sharedObjectLoaderTestOverride = it
    }
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
        clock = fakeClock,
        logger = FakeEmbLogger(ignoredErrors = ignoredInternalErrors),
    )

    private val workerThreadModule: WorkerThreadModule = initWorkerThreadModule(
        fakeInitModule = fakeInitModule,
        workerToFake = workerToFake,
        threadBlockageWatchdogThread = threadBlockageWatchdogThread
    )

    private val fakeCoreModule: CoreModule = FakeCoreModule()
    private val coreModule: CoreModule by lazy { CoreModuleImpl(fakeCoreModule.context, fakeInitModule) }

    @OptIn(ExperimentalApi::class)
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
        configServiceSupplier = { initModule, coreModule, openTelemetryModule, workerThreadModule ->
            val impl = ConfigServiceImpl(
                instrumentedConfig = initModule.instrumentedConfig,
                worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                serializer = initModule.jsonSerializer,
                okHttpClient = initModule.okHttpClient,
                hasConfiguredOtelExporters = openTelemetryModule.otelSdkConfig::hasConfiguredOtelExporters,
                sdkVersion = BuildConfig.VERSION_NAME,
                apiLevel = Build.VERSION.SDK_INT,
                filesDir = coreModule.context.filesDir,
                store = coreModule.store,
                abis = Build.SUPPORTED_ABIS,
                logger = initModule.logger,
            )
            DecoratedConfigService(impl)
        },
        essentialServiceModuleSupplier = { initModule, configService, openTelemetryModule, coreModule, workerThreadModule, _, _ ->
            EssentialServiceModuleImpl(
                initModule = initModule,
                configService = configService,
                openTelemetryModule = openTelemetryModule,
                coreModule = coreModule,
                workerThreadModule = workerThreadModule,
                lifecycleOwnerProvider = { fakeLifecycleOwner },
                networkConnectivityServiceProvider = { fakeNetworkConnectivityService },
            )
        },
        deliveryModuleSupplier = { configService, initModule, otelModule, workerThreadModule, coreModule, essentialServiceModule, _, _, _, _ ->
            DeliveryModuleImpl(
                configService = configService,
                initModule = initModule,
                otelModule = otelModule,
                workerThreadModule = workerThreadModule,
                coreModule = coreModule,
                essentialServiceModule = essentialServiceModule,
                requestExecutionServiceProvider = null,
                payloadStorageServiceProvider = fakePayloadStorageService?.let { { it } },
                cacheStorageServiceProvider = fakeCacheStorageService?.let { { it } },
                deliveryTracer = deliveryTracer
            )
        },
        threadBlockageServiceSupplier = { args ->
            if (threadBlockageWatchdogThread != null) {
                createThreadBlockageService(args)
            } else {
                null
            }
        },
        instrumentationModuleSupplier = {
                initModule,
                openTelemetryModule,
                workerThreadModule,
                configModule,
                essentialServiceModule,
                coreModule,
                storageModule,
            ->
            val impl = InstrumentationModuleImpl(
                initModule,
                openTelemetryModule,
                workerThreadModule,
                configModule,
                essentialServiceModule,
                coreModule,
                storageModule,
            )
            object : InstrumentationModule {
                override val instrumentationRegistry: InstrumentationRegistry = FakeInstrumentationRegistry(impl.instrumentationRegistry)
                override val instrumentationArgs: InstrumentationArgs = impl.instrumentationArgs
            }
        }
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

    fun getClock(): FakeClock = fakeClock

    fun getSpanSink(): SpanSink = fakeInitModule.openTelemetryModule.spanSink

    fun getCurrentSessionSpan(): CurrentSessionSpan = fakeInitModule.openTelemetryModule.currentSessionSpan

    fun getEmbLogger(): FakeEmbLogger = fakeInitModule.logger as FakeEmbLogger

    fun getFakedWorkerExecutor(): BlockingScheduledExecutorService = (workerThreadModule as FakeWorkerThreadModule).executor

    fun getStore(): KeyValueStore = coreModule.store

    private companion object {
        fun initWorkerThreadModule(
            fakeInitModule: FakeInitModule,
            workerToFake: Worker.Background?,
            threadBlockageWatchdogThread: Thread?,
        ): WorkerThreadModule =
            if (workerToFake == null) {
                WorkerThreadModuleImpl()
            } else {
                FakeWorkerThreadModule(
                    fakeInitModule = fakeInitModule,
                    testWorker = workerToFake,
                    threadBlockageMonitoringThread = threadBlockageWatchdogThread
                )
            }
    }

    private class DecoratedConfigService(private val impl: ConfigService): ConfigService by impl {
        override val buildInfo: BuildInfo = BuildInfo(
            "fakeBuildId",
            "fakeBuildType",
            "fakeBuildFlavor",
            "fakeRnBundleId",
            "2.5.1",
            "99",
            "com.fake.package",
        )
        override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A
    }
}
