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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

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
) {
    lateinit var coreModule: CoreModule
        private set

    lateinit var configModule: ConfigModule
        private set

    lateinit var workerThreadModule: WorkerThreadModule
        private set

    lateinit var storageModule: StorageModule
        private set

    lateinit var essentialServiceModule: EssentialServiceModule
        private set

    lateinit var dataCaptureServiceModule: DataCaptureServiceModule
        private set

    lateinit var deliveryModule: DeliveryModule
        private set

    lateinit var anrModule: AnrModule
        private set

    lateinit var logModule: LogModule
        private set

    lateinit var nativeCoreModule: NativeCoreModule
        private set

    lateinit var nativeFeatureModule: NativeFeatureModule
        private set

    lateinit var instrumentationModule: InstrumentationModule
        private set

    lateinit var featureModule: FeatureModule
        private set

    lateinit var sessionOrchestrationModule: SessionOrchestrationModule
        private set

    lateinit var payloadSourceModule: PayloadSourceModule
        private set

    @Volatile
    var initialized: AtomicBoolean = AtomicBoolean(false)

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
            synchronized(initialized) {
                if (isInitialized()) {
                    return false
                }
                val result = initImpl(context, sdkStartTimeMs, versionChecker)
                initialized.set(result)
                return result
            }
        } finally {
            EmbTrace.end()
        }
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun initImpl(
        context: Context,
        sdkStartTimeMs: Long,
        versionChecker: VersionChecker = BuildVersionChecker,
    ): Boolean {
        coreModule = init(
            module = CoreModule::class,
            initAction = { coreModuleSupplier(context, initModule) }
        )

        workerThreadModule = init(
            module = WorkerThreadModule::class,
            initAction = {
                workerThreadModuleSupplier()
            }
        )

        EmbTrace.trace("span-service-init") {
            openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
        }

        configModule = init(
            module = ConfigModule::class,
            initAction = {
                configModuleSupplier(
                    initModule,
                    coreModule,
                    openTelemetryModule,
                    workerThreadModule,
                )
            },
            postAction = { module ->
                openTelemetryModule.applyConfiguration(
                    sensitiveKeysBehavior = module.configService.sensitiveKeysBehavior,
                    bypassValidation = module.configService.isOnlyUsingOtelExporters(),
                    otelBehavior = module.configService.otelBehavior
                )
            }
        )

        EmbTrace.trace("sdk-disable-check") {
            // kick off config HTTP request first so the SDK can't get in a permanently disabled state
            EmbTrace.trace("load-config-response") {
                configModule.combinedRemoteConfigSource?.scheduleConfigRequests()
            }

            EmbTrace.trace("behavior-check") {
                if (configModule.configService.sdkModeBehavior.isSdkDisabled()) {
                    return false
                }
            }
        }

        storageModule = init(
            module = StorageModule::class,
            initAction = {
                storageModuleSupplier(initModule, coreModule, workerThreadModule)
            }
        )

        essentialServiceModule = init(
            module = EssentialServiceModule::class,
            initAction = {
                essentialServiceModuleSupplier(
                    initModule,
                    configModule,
                    openTelemetryModule,
                    coreModule,
                    workerThreadModule,
                    { null },
                    { null },
                )
            },
            postAction = { module ->
                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                    EmbTrace.trace("network-connectivity-registration") {
                        module.networkConnectivityService.register()
                    }
                }
            }
        )

        instrumentationModule = init(
            module = InstrumentationModule::class,
            initAction = {
                instrumentationModuleSupplier(
                    initModule,
                    workerThreadModule,
                    configModule,
                    essentialServiceModule,
                    coreModule,
                )
            }
        )

        featureModule = init(
            module = FeatureModule::class,
            initAction = {
                featureModuleSupplier(
                    instrumentationModule,
                    configModule.configService,
                    storageModule,
                )
            },
            postAction = { module ->
                initModule.logger.errorHandlerProvider = { module.internalErrorDataSource.dataSource }
            }
        )

        dataCaptureServiceModule = init(
            module = DataCaptureServiceModule::class,
            initAction = {
                dataCaptureServiceModuleSupplier(
                    initModule,
                    openTelemetryModule,
                    configModule.configService,
                    versionChecker,
                )
            }
        )

        EmbTrace.trace("startup-tracker") {
            coreModule.application.registerActivityLifecycleCallbacks(
                dataCaptureServiceModule.startupTracker
            )
        }

        deliveryModule = init(
            module = DeliveryModule::class,
            initAction = {
                deliveryModuleSupplier(
                    configModule,
                    initModule,
                    openTelemetryModule,
                    workerThreadModule,
                    coreModule,
                    essentialServiceModule,
                    null,
                    null,
                    null,
                    null
                )
            },
            postAction = { module ->
                module.payloadCachingService?.run {
                    openTelemetryModule.spanRepository.setSpanUpdateNotifier {
                        reportBackgroundActivityStateChange()
                    }
                }
            }
        )

        anrModule = init(
            module = AnrModule::class,
            initAction = {
                anrModuleSupplier(
                    instrumentationModule.instrumentationArgs,
                    essentialServiceModule.appStateTracker
                )
            },
            postAction = { module ->
                module.anrService?.startAnrCapture()
            }
        )

        payloadSourceModule = init(
            module = PayloadSourceModule::class,
            initAction = {
                payloadSourceModuleSupplier(
                    initModule,
                    coreModule,
                    workerThreadModule,
                    essentialServiceModule,
                    configModule,
                    { nativeCoreModule.symbolService.symbolsForCurrentArch },
                    openTelemetryModule,
                    { anrModule.anrOtelMapper },
                    deliveryModule
                )
            },
            postAction = { module ->
                module.metadataService.precomputeValues()
            }
        )

        nativeCoreModule = init(
            module = NativeCoreModule::class,
            initAction = {
                nativeCoreModuleSupplier(
                    configModule,
                    workerThreadModule,
                    storageModule,
                    essentialServiceModule,
                    instrumentationModule,
                    openTelemetryModule,
                    { null },
                    { null },
                    { null },
                )
            }
        )

        nativeFeatureModule = init(
            module = NativeFeatureModule::class,
            initAction = {
                nativeFeatureModuleSupplier(
                    nativeCoreModule,
                    instrumentationModule,
                )
            },
            postAction = { module ->
                nativeCoreModule.sharedObjectLoader.loadEmbraceNative()
                nativeCoreModule.nativeCrashHandlerInstaller?.install()
            }
        )

        logModule = init(
            module = LogModule::class,
            initAction = {
                logModuleSupplier(
                    initModule,
                    openTelemetryModule,
                    essentialServiceModule,
                    configModule,
                    deliveryModule,
                    workerThreadModule,
                    payloadSourceModule,
                )
            },
            postAction = { module ->
                // Start the log orchestrator
                openTelemetryModule.logSink.registerLogStoredCallback {
                    module.logOrchestrator.onLogsAdded()
                }
            }
        )

        sessionOrchestrationModule = init(
            module = SessionOrchestrationModule::class,
            initAction = {
                sessionOrchestrationModuleSupplier(
                    initModule,
                    openTelemetryModule,
                    coreModule,
                    essentialServiceModule,
                    configModule,
                    deliveryModule,
                    instrumentationModule,
                    payloadSourceModule,
                    dataCaptureServiceModule.startupService,
                    logModule
                )
            },
            postAction = { module ->
                essentialServiceModule.telemetryDestination.sessionUpdateAction =
                    module.sessionOrchestrator::onSessionDataUpdate
            }
        )
        registerListeners()
        return true
    }

    /**
     * Registers objects as listeners for lifecycle/state callbacks.
     */
    private fun registerListeners() {
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
        if (isInitialized()) {
            coreModule.serviceRegistry.close()
            workerThreadModule.close()
            initialized.set(false)
        }
    }

    fun isInitialized(): Boolean = initialized.get()

    private fun <T> init(
        module: KClass<*>,
        initAction: Provider<T>,
        postAction: (module: T) -> Unit = {},
    ): T {
        val name = module.simpleName?.removeSuffix("Module")?.lowercase() ?: "module"
        return EmbTrace.trace("$name-init") {
            initAction().apply(postAction)
        }
    }
}
