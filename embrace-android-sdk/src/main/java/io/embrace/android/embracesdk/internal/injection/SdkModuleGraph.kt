package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.SharedObjectLoader
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.ServiceLoader

/**
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    val initModule: InitModule = EmbTrace.trace("init-module", ::InitModuleImpl),
    val openTelemetryModule: OpenTelemetryModule = EmbTrace.trace("otel-module") {
        OpenTelemetryModuleImpl(initModule)
    },
    private val coreModuleSupplier: CoreModuleSupplier = {
            context: Context,
            initModule: InitModule,
        ->
        CoreModuleImpl(
            context,
            initModule
        )
    },
    private val configModuleSupplier: ConfigModuleSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
            openTelemetryModule: OpenTelemetryModule,
            workerThreadModule: WorkerThreadModule,
        ->
        ConfigModuleImpl(
            initModule,
            coreModule,
            openTelemetryModule,
            workerThreadModule,
        )
    },
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier = { WorkerThreadModuleImpl() },
    private val storageModuleSupplier: StorageModuleSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
            workerThreadModule: WorkerThreadModule,
        ->
        StorageModuleImpl(
            initModule,
            coreModule,
            workerThreadModule
        )
    },
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier = {
            initModule: InitModule,
            configModule: ConfigModule,
            openTelemetryModule: OpenTelemetryModule,
            coreModule: CoreModule,
            workerThreadModule: WorkerThreadModule,
            lifecycleOwnerProvider: Provider<LifecycleOwner?>,
            networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
        ->
        EssentialServiceModuleImpl(
            initModule,
            configModule,
            openTelemetryModule,
            coreModule,
            workerThreadModule,
            lifecycleOwnerProvider,
            networkConnectivityServiceProvider,
        )
    },
    private val featureModuleSupplier: FeatureModuleSupplier = {
            instrumentationModule: InstrumentationModule,
            configService: ConfigService,
            storageModule: StorageModule,
        ->
        FeatureModuleImpl(
            instrumentationModule = instrumentationModule,
            configService = configService,
            storageModule = storageModule,
        )
    },
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier = {
            initModule: InitModule,
            workerThreadModule: WorkerThreadModule,
            configModule: ConfigModule,
            essentialServiceModule: EssentialServiceModule,
            coreModule: CoreModule,
        ->
        InstrumentationModuleImpl(
            initModule,
            workerThreadModule,
            configModule,
            essentialServiceModule,
            coreModule,
        )
    },
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = {
            initModule: InitModule,
            openTelemetryModule: OpenTelemetryModule,
            configService: ConfigService,
            versionChecker: VersionChecker,
        ->
        DataCaptureServiceModuleImpl(
            initModule,
            openTelemetryModule,
            configService,
            versionChecker
        )
    },
    private val deliveryModuleSupplier: DeliveryModuleSupplier = {
            configModule: ConfigModule,
            initModule: InitModule,
            otelModule: OpenTelemetryModule,
            workerThreadModule: WorkerThreadModule,
            coreModule: CoreModule,
            essentialServiceModule: EssentialServiceModule,
            payloadStorageServiceProvider: Provider<PayloadStorageService>?,
            cacheStorageServiceProvider: Provider<PayloadStorageService>?,
            requestExecutionServiceProvider: Provider<RequestExecutionService>?,
            deliveryTracer: DeliveryTracer?,
        ->
        DeliveryModuleImpl(
            configModule,
            initModule,
            otelModule,
            workerThreadModule,
            coreModule,
            essentialServiceModule,
            requestExecutionServiceProvider,
            payloadStorageServiceProvider,
            cacheStorageServiceProvider,
            deliveryTracer
        )
    },
    private val anrModuleSupplier: AnrModuleSupplier = {
            args: InstrumentationArgs,
            appStateTracker: AppStateTracker,
        ->
        AnrModuleImpl(
            args,
            appStateTracker
        )
    },
    private val logModuleSupplier: LogModuleSupplier = {
            initModule: InitModule,
            openTelemetryModule: OpenTelemetryModule,
            essentialServiceModule: EssentialServiceModule,
            configModule: ConfigModule,
            deliveryModule: DeliveryModule,
            workerThreadModule: WorkerThreadModule,
            payloadSourceModule: PayloadSourceModule,
        ->
        LogModuleImpl(
            initModule,
            openTelemetryModule,
            essentialServiceModule,
            configModule,
            deliveryModule,
            workerThreadModule,
            payloadSourceModule,
        )
    },
    private val nativeCoreModuleSupplier: NativeCoreModuleSupplier = {
            configModule: ConfigModule,
            workerThreadModule: WorkerThreadModule,
            storageModule: StorageModule,
            essentialServiceModule: EssentialServiceModule,
            instrumentationModule: InstrumentationModule,
            otelModule: OpenTelemetryModule,
            delegateProvider: Provider<JniDelegate?>,
            sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
            symbolServiceProvider: Provider<SymbolService?>,
        ->
        NativeCoreModuleImpl(
            configModule,
            workerThreadModule,
            storageModule,
            essentialServiceModule,
            instrumentationModule,
            otelModule,
            delegateProvider,
            sharedObjectLoaderProvider,
            symbolServiceProvider,
        )
    },
    private val nativeFeatureModuleSupplier: NativeFeatureModuleSupplier = {
            nativeCoreModule: NativeCoreModule,
            instrumentationModule: InstrumentationModule,
        ->
        NativeFeatureModuleImpl(
            nativeCoreModule,
            instrumentationModule,
        )
    },
    private val sessionOrchestrationModuleSupplier: SessionOrchestrationModuleSupplier = {
            initModule: InitModule,
            openTelemetryModule: OpenTelemetryModule,
            coreModule: CoreModule,
            essentialServiceModule: EssentialServiceModule,
            configModule: ConfigModule,
            deliveryModule: DeliveryModule,
            instrumentationModule: InstrumentationModule,
            payloadSourceModule: PayloadSourceModule,
            startupService: StartupService,
            logModule: LogModule,
        ->
        SessionOrchestrationModuleImpl(
            initModule,
            openTelemetryModule,
            coreModule,
            essentialServiceModule,
            configModule,
            deliveryModule,
            instrumentationModule,
            payloadSourceModule,
            startupService,
            logModule
        )
    },
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
            workerThreadModule: WorkerThreadModule,
            essentialServiceModule: EssentialServiceModule,
            configModule: ConfigModule,
            nativeSymbolsProvider: Provider<Map<String, String>?>,
            otelModule: OpenTelemetryModule, otelPayloadMapperProvider: Provider<OtelPayloadMapper?>,
            deliveryModule: DeliveryModule,
        ->
        PayloadSourceModuleImpl(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configModule,
            nativeSymbolsProvider,
            otelModule,
            otelPayloadMapperProvider,
            deliveryModule,
        )
    },
) : ModuleGraph {

    @Volatile
    private var delegate: ModuleGraph = UninitializedModuleGraph
    override val coreModule: CoreModule get() = delegate.coreModule
    override val configModule: ConfigModule get() = delegate.configModule
    override val workerThreadModule: WorkerThreadModule get() = delegate.workerThreadModule
    override val storageModule: StorageModule get() = delegate.storageModule
    override val essentialServiceModule: EssentialServiceModule get() = delegate.essentialServiceModule
    override val dataCaptureServiceModule: DataCaptureServiceModule get() = delegate.dataCaptureServiceModule
    override val deliveryModule: DeliveryModule get() = delegate.deliveryModule
    override val anrModule: AnrModule get() = delegate.anrModule
    override val logModule: LogModule get() = delegate.logModule
    override val nativeCoreModule: NativeCoreModule get() = delegate.nativeCoreModule
    override val nativeFeatureModule: NativeFeatureModule get() = delegate.nativeFeatureModule
    override val instrumentationModule: InstrumentationModule get() = delegate.instrumentationModule
    override val featureModule: FeatureModule get() = delegate.featureModule
    override val sessionOrchestrationModule: SessionOrchestrationModule get() = delegate.sessionOrchestrationModule
    override val payloadSourceModule: PayloadSourceModule get() = delegate.payloadSourceModule

    /**
     * Returns true when the call has triggered an initialization, false if initialization is already in progress or is complete.
     */
    fun init(
        context: Context,
        sdkStartTimeMs: Long,
        versionChecker: VersionChecker = BuildVersionChecker,
    ): Boolean {
        try {
            EmbTrace.start("modules-init")
            if (isInitialized()) {
                return false
            }
            synchronized(delegate) {
                if (isInitialized()) {
                    return false
                }
                delegate = InitializedModuleGraph(
                    context,
                    sdkStartTimeMs,
                    versionChecker,
                    initModule,
                    openTelemetryModule,
                    coreModuleSupplier,
                    configModuleSupplier,
                    workerThreadModuleSupplier,
                    storageModuleSupplier,
                    essentialServiceModuleSupplier,
                    featureModuleSupplier,
                    instrumentationModuleSupplier,
                    dataCaptureServiceModuleSupplier,
                    deliveryModuleSupplier,
                    anrModuleSupplier,
                    logModuleSupplier,
                    nativeCoreModuleSupplier,
                    nativeFeatureModuleSupplier,
                    sessionOrchestrationModuleSupplier,
                    payloadSourceModuleSupplier
                )
                postInit()
                return isInitialized()
            }
        } catch (ignored: SdkDisabledException) {
            // do nothing - avoid instantiating SDK code any more than necessary.
            return false
        } finally {
            EmbTrace.end()
        }
    }

    fun postInit() {
        openTelemetryModule.applyConfiguration(
            sensitiveKeysBehavior = configModule.configService.sensitiveKeysBehavior,
            bypassValidation = configModule.configService.isOnlyUsingOtelExporters(),
            otelBehavior = configModule.configService.otelBehavior
        )

        workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
            EmbTrace.trace("network-connectivity-registration") {
                essentialServiceModule.networkConnectivityService.register()
            }
        }

        initModule.logger.errorHandlerProvider = { featureModule.internalErrorDataSource.dataSource }

        EmbTrace.trace("startup-tracker") {
            coreModule.application.registerActivityLifecycleCallbacks(
                dataCaptureServiceModule.startupTracker
            )
        }
        deliveryModule.payloadCachingService?.run {
            openTelemetryModule.spanRepository.setSpanUpdateNotifier {
                reportBackgroundActivityStateChange()
            }
        }

        anrModule.anrService?.startAnrCapture()

        payloadSourceModule.metadataService.precomputeValues()

        nativeCoreModule.sharedObjectLoader.loadEmbraceNative()
        nativeCoreModule.nativeCrashHandlerInstaller?.install()

        // Start the log orchestrator
        openTelemetryModule.logSink.registerLogStoredCallback {
            logModule.logOrchestrator.onLogsAdded()
        }

        essentialServiceModule.telemetryDestination.sessionUpdateAction =
            sessionOrchestrationModule.sessionOrchestrator::onSessionDataUpdate

        featureModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
            workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker)
        )
    }

    /**
     * Registers objects as listeners for lifecycle/state callbacks.
     */
    fun registerListeners() {
        EmbTrace.trace("service-registration") {
            with(coreModule.serviceRegistry) {
                registerService(lazy { configModule.configService })
                registerService(lazy { configModule.remoteConfigSource })
                registerService(lazy { configModule.configService.networkBehavior.domainCountLimiter })

                registerServices(
                    lazy { essentialServiceModule.appStateTracker },
                    lazy { essentialServiceModule.activityLifecycleTracker },
                    lazy { essentialServiceModule.networkConnectivityService }
                )
                registerServices(
                    lazy { dataCaptureServiceModule.appStartupDataCollector },
                    lazy { dataCaptureServiceModule.uiLoadDataListener },
                )
                registerServices(
                    lazy { anrModule.anrService }
                )
                registerService(lazy { logModule.attachmentService })
                registerService(lazy { logModule.logService })

                // registration ignored after this point
                registerAppStateListeners(essentialServiceModule.appStateTracker)
                registerMemoryCleanerListeners(sessionOrchestrationModule.memoryCleanerService)
                registerActivityLifecycleListeners(essentialServiceModule.activityLifecycleTracker)
            }
        }
    }

    fun loadInstrumentation() {
        val registry = instrumentationModule.instrumentationRegistry
        val instrumentationProviders = ServiceLoader.load(InstrumentationProvider::class.java)
        registry.loadInstrumentations(instrumentationProviders, instrumentationModule.instrumentationArgs)
    }

    fun postLoadInstrumentation() {
        // setup crash teardown handlers
        val registry = instrumentationModule.instrumentationRegistry
        registry.findByType(JvmCrashDataSource::class)?.apply {
            anrModule.anrService?.let(::addCrashTeardownHandler)
            addCrashTeardownHandler(logModule.logOrchestrator)
            addCrashTeardownHandler(sessionOrchestrationModule.sessionOrchestrator)
            addCrashTeardownHandler(featureModule.crashMarker)
            deliveryModule.payloadStore?.let(::addCrashTeardownHandler)
        }
        registry.findByType(NetworkStatusDataSource::class)?.let {
            essentialServiceModule.networkConnectivityService.addNetworkConnectivityListener(it)
        }
    }

    fun stopServices() {
        if (!isInitialized()) {
            return
        }
        synchronized(delegate) {
            if (isInitialized()) {
                coreModule.serviceRegistry.close()
                workerThreadModule.close()
                delegate = UninitializedModuleGraph
            }
        }
    }

    fun isInitialized(): Boolean = delegate != UninitializedModuleGraph
}
