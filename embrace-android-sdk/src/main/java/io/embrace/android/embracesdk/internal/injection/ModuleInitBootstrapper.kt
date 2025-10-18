package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.capture.envelope.session.OtelPayloadMapperImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.network.http.HttpUrlConnectionTracker.registerFactory
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    val logger: EmbLogger = EmbTrace.trace("logger-init", ::EmbLoggerImpl),
    val clock: Clock = NormalizedIntervalClock(),
    val initModule: InitModule = EmbTrace.trace("init-module") {
        createInitModule(
            clock = clock,
            logger = logger
        )
    },
    val openTelemetryModule: OpenTelemetryModule = EmbTrace.trace("otel-module") {
        createOpenTelemetryModule(initModule)
    },
    private val coreModuleSupplier: CoreModuleSupplier = ::createCoreModule,
    private val configModuleSupplier: ConfigModuleSupplier = ::createConfigModule,
    private val systemServiceModuleSupplier: SystemServiceModuleSupplier = ::createSystemServiceModule,
    private val androidServicesModuleSupplier: AndroidServicesModuleSupplier = ::createAndroidServicesModule,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier = ::createWorkerThreadModule,
    private val storageModuleSupplier: StorageModuleSupplier = ::createStorageModuleSupplier,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier = ::createEssentialServiceModule,
    private val featureModuleSupplier: FeatureModuleSupplier = ::createFeatureModule,
    private val dataSourceModuleSupplier: DataSourceModuleSupplier = ::createDataSourceModule,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = ::createDataCaptureServiceModule,
    private val deliveryModuleSupplier: DeliveryModuleSupplier = ::createDeliveryModule,
    private val anrModuleSupplier: AnrModuleSupplier = ::createAnrModule,
    private val logModuleSupplier: LogModuleSupplier = ::createLogModule,
    private val nativeCoreModuleSupplier: NativeCoreModuleSupplier = ::createNativeCoreModule,
    private val nativeFeatureModuleSupplier: NativeFeatureModuleSupplier = ::createNativeFeatureModule,
    private val sessionOrchestrationModuleSupplier: SessionOrchestrationModuleSupplier = ::createSessionOrchestrationModule,
    private val crashModuleSupplier: CrashModuleSupplier = ::createCrashModule,
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier = ::createPayloadSourceModule,
) {
    lateinit var coreModule: CoreModule
        private set

    lateinit var configModule: ConfigModule
        private set

    lateinit var workerThreadModule: WorkerThreadModule
        private set

    lateinit var systemServiceModule: SystemServiceModule
        private set

    lateinit var androidServicesModule: AndroidServicesModule
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

    lateinit var dataSourceModule: DataSourceModule
        private set

    lateinit var featureModule: FeatureModule
        private set

    lateinit var sessionOrchestrationModule: SessionOrchestrationModule
        private set

    lateinit var crashModule: CrashModule
        private set

    lateinit var payloadSourceModule: PayloadSourceModule
        private set

    @Volatile
    var initialized: AtomicBoolean = AtomicBoolean(false)

    /**
     * Returns true when the call has triggered an initialization, false if initialization is already in progress or is complete.
     */

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
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
                val result = if (!isInitialized()) {
                    coreModule = init(CoreModule::class) { coreModuleSupplier(context, initModule) }

                    workerThreadModule = init(WorkerThreadModule::class) {
                        workerThreadModuleSupplier()
                    }

                    postInit(OpenTelemetryModule::class) {
                        EmbTrace.trace("span-service-init") {
                            openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
                        }
                    }

                    androidServicesModule = init(AndroidServicesModule::class) {
                        androidServicesModuleSupplier(initModule, coreModule)
                    }

                    configModule = init(ConfigModule::class) {
                        configModuleSupplier(
                            initModule,
                            coreModule,
                            openTelemetryModule,
                            workerThreadModule,
                            androidServicesModule,
                        )
                    }

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

                    val serviceRegistry = coreModule.serviceRegistry
                    postInit(ConfigModule::class) {
                        serviceRegistry.registerService(lazy { configModule.configService })
                        serviceRegistry.registerService(lazy { configModule.remoteConfigSource })
                        openTelemetryModule.applyConfiguration(
                            sensitiveKeysBehavior = configModule.configService.sensitiveKeysBehavior,
                            bypassValidation = configModule.configService.isOnlyUsingOtelExporters(),
                            otelBehavior = configModule.configService.otelBehavior
                        )
                    }

                    systemServiceModule = init(SystemServiceModule::class) {
                        systemServiceModuleSupplier(coreModule, versionChecker)
                    }

                    storageModule = init(StorageModule::class) {
                        storageModuleSupplier(initModule, coreModule, workerThreadModule)
                    }

                    essentialServiceModule = init(EssentialServiceModule::class) {
                        essentialServiceModuleSupplier(
                            initModule,
                            configModule,
                            openTelemetryModule,
                            coreModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            { null },
                            { null }
                        )
                    }
                    postInit(EssentialServiceModule::class) {
                        with(essentialServiceModule) {
                            serviceRegistry.registerServices(
                                lazy { essentialServiceModule.processStateService },
                                lazy { activityLifecycleTracker },
                                lazy { networkConnectivityService }
                            )

                            val networkBehavior = configModule.configService.networkBehavior
                            if (networkBehavior.isHttpUrlConnectionCaptureEnabled()) {
                                EmbTrace.trace("network-monitoring-installation") {
                                    registerFactory(networkBehavior.isRequestContentLengthCaptureEnabled())
                                }
                            }
                            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                                EmbTrace.trace("network-connectivity-registration") {
                                    essentialServiceModule.networkConnectivityService.register()
                                }
                            }
                        }
                    }

                    anrModule = init(AnrModule::class) {
                        anrModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            configModule.configService,
                            workerThreadModule,
                            essentialServiceModule.processStateService
                        )
                    }

                    dataSourceModule = init(DataSourceModule::class) {
                        dataSourceModuleSupplier(
                            initModule,
                            workerThreadModule
                        )
                    }

                    featureModule = init(FeatureModule::class) {
                        featureModuleSupplier(
                            dataSourceModule.embraceFeatureRegistry,
                            coreModule,
                            initModule,
                            openTelemetryModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            essentialServiceModule.logWriter,
                            configModule.configService,
                        )
                    }
                    postInit(FeatureModule::class) {
                        initModule.logger.errorHandlerProvider = { featureModule.internalErrorDataSource.dataSource }
                    }

                    dataCaptureServiceModule = init(DataCaptureServiceModule::class) {
                        dataCaptureServiceModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            configModule.configService,
                            versionChecker,
                            featureModule
                        )
                    }

                    EmbTrace.trace("startup-tracker") {
                        coreModule.application.registerActivityLifecycleCallbacks(
                            dataCaptureServiceModule.startupTracker
                        )
                    }

                    postInit(DataCaptureServiceModule::class) {
                        serviceRegistry.registerServices(
                            lazy { dataCaptureServiceModule.appStartupDataCollector },
                            lazy { dataCaptureServiceModule.activityBreadcrumbTracker },
                            lazy { dataCaptureServiceModule.pushNotificationService },
                            lazy { dataCaptureServiceModule.uiLoadDataListener },
                        )
                    }

                    deliveryModule = init(DeliveryModule::class) {
                        deliveryModuleSupplier(
                            configModule,
                            initModule,
                            openTelemetryModule,
                            workerThreadModule,
                            coreModule,
                            essentialServiceModule,
                            androidServicesModule,
                            null,
                            null,
                            null,
                            null
                        )
                    }
                    postInit(DeliveryModule::class) {
                        deliveryModule.payloadCachingService?.run {
                            openTelemetryModule.spanRepository.setSpanUpdateNotifier {
                                reportBackgroundActivityStateChange()
                            }
                        }
                    }

                    postInit(AnrModule::class) {
                        serviceRegistry.registerServices(
                            lazy { anrModule.anrService }
                        )
                        anrModule.anrService?.startAnrCapture()
                    }

                    payloadSourceModule = init(PayloadSourceModule::class) {
                        payloadSourceModuleSupplier(
                            initModule,
                            coreModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            essentialServiceModule,
                            configModule,
                            { nativeCoreModule.symbolService.symbolsForCurrentArch },
                            openTelemetryModule,
                            { OtelPayloadMapperImpl(anrModule.anrOtelMapper) },
                            deliveryModule
                        )
                    }
                    postInit(PayloadSourceModule::class) {
                        payloadSourceModule.metadataService.precomputeValues()
                    }

                    nativeCoreModule = init(NativeCoreModule::class) {
                        nativeCoreModuleSupplier(
                            initModule,
                            coreModule,
                            payloadSourceModule,
                            workerThreadModule,
                            configModule,
                            storageModule,
                            essentialServiceModule,
                            openTelemetryModule,
                            { null },
                            { null },
                            { null },
                        )
                    }

                    nativeFeatureModule = init(NativeFeatureModule::class) {
                        nativeFeatureModuleSupplier(
                            initModule,
                            essentialServiceModule,
                            configModule,
                            androidServicesModule,
                            nativeCoreModule
                        )
                    }

                    postInit(NativeFeatureModule::class) {
                        nativeCoreModule.sharedObjectLoader.loadEmbraceNative()
                        nativeCoreModule.nativeCrashHandlerInstaller?.install()
                    }

                    logModule = init(LogModule::class) {
                        logModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            androidServicesModule,
                            essentialServiceModule,
                            configModule,
                            deliveryModule,
                            workerThreadModule,
                            payloadSourceModule
                        )
                    }

                    postInit(LogModule::class) {
                        serviceRegistry.registerService(lazy { logModule.attachmentService })
                        serviceRegistry.registerService(lazy { logModule.logService })
                        // Start the log orchestrator
                        openTelemetryModule.logSink.registerLogStoredCallback {
                            logModule.logOrchestrator.onLogsAdded()
                        }
                    }

                    sessionOrchestrationModule = init(SessionOrchestrationModule::class) {
                        sessionOrchestrationModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            androidServicesModule,
                            essentialServiceModule,
                            configModule,
                            deliveryModule,
                            dataSourceModule,
                            payloadSourceModule,
                            dataCaptureServiceModule.startupService,
                            logModule
                        )
                    }

                    postInit(FeatureModule::class) {
                        essentialServiceModule.networkConnectivityService.addNetworkConnectivityListener {
                            featureModule.networkStatusDataSource.dataSource?.onNetworkConnectivityStatusChanged(it)
                        }
                    }

                    crashModule = init(CrashModule::class) {
                        crashModuleSupplier(
                            initModule,
                            storageModule,
                            essentialServiceModule,
                            configModule,
                            androidServicesModule,
                        )
                    }

                    postInit(CrashModule::class) {
                        serviceRegistry.registerService(lazy { crashModule.crashDataSource })
                        with(crashModule.crashDataSource) {
                            addCrashTeardownHandler(lazy { anrModule.anrService })
                            addCrashTeardownHandler(lazy { logModule.logOrchestrator })
                            addCrashTeardownHandler(lazy { sessionOrchestrationModule.sessionOrchestrator })
                            addCrashTeardownHandler(lazy { deliveryModule.payloadStore })
                        }
                    }

                    // Sets up the registered services. This method is called after the SDK has been started and no more services can
                    // be added to the registry. It sets listeners for any services that were registered.
                    EmbTrace.trace("service-registration") {
                        serviceRegistry.closeRegistration()
                        serviceRegistry.registerActivityListeners(essentialServiceModule.processStateService)
                        serviceRegistry.registerMemoryCleanerListeners(sessionOrchestrationModule.memoryCleanerService)
                        serviceRegistry.registerActivityLifecycleListeners(essentialServiceModule.activityLifecycleTracker)
                    }

                    // Verify that the ProcessStateService is fully initialized at this point, and log otherwise.
                    if (!essentialServiceModule.processStateService.isInitialized()) {
                        logger.trackInternalError(
                            type = InternalErrorType.PROCESS_STATE_CALLBACK_FAIL,
                            throwable = IllegalStateException("ProcessStateService not initialized"),
                        )
                    }
                    true
                } else {
                    false
                }
                initialized.set(result)
                return result
            }
        } finally {
            EmbTrace.end()
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

    private fun <T> init(module: KClass<*>, provider: Provider<T>): T =
        EmbTrace.trace("${toSectionName(module)}-init") { provider() }

    private fun <T> postInit(module: KClass<*>, code: () -> T): T =
        EmbTrace.trace("${toSectionName(module)}-post-init") { code() }

    // This is called twice for each input - memoizing/caching is not worth the hassle
    private fun toSectionName(klass: KClass<*>): String =
        klass.simpleName?.removeSuffix("Module")?.lowercase(Locale.ENGLISH) ?: "module"
}
