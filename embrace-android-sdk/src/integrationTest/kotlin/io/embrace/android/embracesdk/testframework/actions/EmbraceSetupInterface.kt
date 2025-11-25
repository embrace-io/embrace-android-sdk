package io.embrace.android.embracesdk.testframework.actions

import android.content.pm.PackageInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.FakeSymbolService
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.envelope.PackageVersionInfo
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.ConfigModuleImpl
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.injection.DeliveryModuleImpl
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModuleImpl
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.NoopAnrService
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.ExperimentalApi

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface(
    workerToFake: Worker.Background? = null,
    private val anrMonitoringThread: Thread? = null,
    fakeStorageLayer: Boolean = false,
    val ignoredInternalErrors: List<InternalErrorType> = emptyList(),
    val fakeClock: FakeClock = FakeClock(currentTime = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS),
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
        clock = fakeClock,
        logger = FakeEmbLogger(ignoredErrors = ignoredInternalErrors),
        processIdentifier = processIdentifier
    )

    private val workerThreadModule: WorkerThreadModule = initWorkerThreadModule(
        fakeInitModule = fakeInitModule,
        workerToFake = workerToFake,
        anrMonitoringThread = anrMonitoringThread
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
        configModuleSupplier = { initModule, coreModule, openTelemetryModule, workerThreadModule ->
            val impl = ConfigModuleImpl(initModule, coreModule, openTelemetryModule, workerThreadModule)
            DecoratedConfigModule(impl)
        },
        essentialServiceModuleSupplier = { initModule, configModule, openTelemetryModule, coreModule, workerThreadModule, _, _ ->
            EssentialServiceModuleImpl(
                initModule = initModule,
                configModule = configModule,
                openTelemetryModule = openTelemetryModule,
                coreModule = coreModule,
                workerThreadModule = workerThreadModule,
                lifecycleOwnerProvider = { fakeLifecycleOwner },
                networkConnectivityServiceProvider = { fakeNetworkConnectivityService },
            )
        },
        deliveryModuleSupplier = { configModule, initModule, otelModule, workerThreadModule, coreModule, essentialServiceModule, _, _, _, _ ->
            DeliveryModuleImpl(
                configModule = configModule,
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
        anrModuleSupplier = { instrumentationModule, _ ->
            if (anrMonitoringThread != null) {
                AnrModuleImpl(
                    instrumentationModule,
                    FakeAppStateTracker()
                )
            } else {
                val fakeAnrService = NoopAnrService
                FakeAnrModule(
                    anrService = fakeAnrService,
                    anrOtelMapper = AnrOtelMapper(
                        anrService = fakeAnrService,
                        clock = fakeInitModule.clock,
                        telemetryDestination = instrumentationModule.destination
                    )
                )
            }
        },
        nativeCoreModuleSupplier = { workerThreadModule, storageModule, essentialServiceModule, instrumentationArgs, _, _, _ ->
            NativeCoreModuleImpl(
                workerThreadModule = workerThreadModule,
                storageModule = storageModule,
                essentialServiceModule = essentialServiceModule,
                args = instrumentationArgs,
                delegateProvider = { fakeJniDelegate },
                sharedObjectLoaderProvider = ::FakeSharedObjectLoader,
                symbolServiceProvider = { fakeSymbolService }
            )
        },
        instrumentationModuleSupplier = {
                initModule,
                openTelemetryModule,
                workerThreadModule,
                configModule,
                essentialServiceModule,
                coreModule,
            ->
            val impl = InstrumentationModuleImpl(
                initModule,
                openTelemetryModule,
                workerThreadModule,
                configModule,
                essentialServiceModule,
                coreModule,
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

    fun getPreferencesService(): PreferencesService = coreModule.preferencesService

    private companion object {
        fun initWorkerThreadModule(
            fakeInitModule: FakeInitModule,
            workerToFake: Worker.Background?,
            anrMonitoringThread: Thread?,
        ): WorkerThreadModule =
            if (workerToFake == null) {
                WorkerThreadModuleImpl()
            } else {
                FakeWorkerThreadModule(
                    fakeInitModule = fakeInitModule,
                    testWorker = workerToFake,
                    anrMonitoringThread = anrMonitoringThread
                )
            }
    }

    private class DecoratedConfigModule(private val impl: ConfigModule) : ConfigModule by impl {
        override val appEnvironment: AppEnvironment = AppEnvironment(true)
        override val buildInfo: BuildInfo = BuildInfo(
            "fakeBuildId",
            "fakeBuildType",
            "fakeBuildFlavor",
            "fakeRnBundleId",
        )
        override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A

        @Suppress("DEPRECATION")
        override val packageVersionInfo = PackageVersionInfo(PackageInfo().apply {
            packageName = "com.fake.package"
            versionName = "2.5.1"
            versionCode = 99
        })
    }
}
