package io.embrace.android.embracesdk.injection

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.anr.ndk.isUnityMainThread
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.network.http.HttpUrlConnectionTracker.registerFactory
import io.embrace.android.embracesdk.internal.utils.AndroidServicesModuleSupplier
import io.embrace.android.embracesdk.internal.utils.AnrModuleSupplier
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.CoreModuleSupplier
import io.embrace.android.embracesdk.internal.utils.CrashModuleSupplier
import io.embrace.android.embracesdk.internal.utils.CustomerLogModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DataContainerModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DataSourceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DeliveryModuleSupplier
import io.embrace.android.embracesdk.internal.utils.EssentialServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.NativeModuleSupplier
import io.embrace.android.embracesdk.internal.utils.PayloadModuleSupplier
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.SdkObservabilityModuleSupplier
import io.embrace.android.embracesdk.internal.utils.SessionModuleSupplier
import io.embrace.android.embracesdk.internal.utils.StorageModuleSupplier
import io.embrace.android.embracesdk.internal.utils.SystemServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.WorkerThreadModuleSupplier
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.ndk.NativeModuleImpl
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.TaskPriority
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import java.util.Locale
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.reflect.KClass

/**
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    val initModule: InitModule = InitModuleImpl(),
    val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(initModule),
    private val coreModuleSupplier: CoreModuleSupplier = ::CoreModuleImpl,
    private val systemServiceModuleSupplier: SystemServiceModuleSupplier = ::SystemServiceModuleImpl,
    private val androidServicesModuleSupplier: AndroidServicesModuleSupplier = ::AndroidServicesModuleImpl,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier = ::WorkerThreadModuleImpl,
    private val storageModuleSupplier: StorageModuleSupplier = ::StorageModuleImpl,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier = ::EssentialServiceModuleImpl,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = ::DataCaptureServiceModuleImpl,
    private val deliveryModuleSupplier: DeliveryModuleSupplier = ::DeliveryModuleImpl,
    private val anrModuleSupplier: AnrModuleSupplier = ::AnrModuleImpl,
    private val sdkObservabilityModuleSupplier: SdkObservabilityModuleSupplier = ::SdkObservabilityModuleImpl,
    private val customerLogModuleSupplier: CustomerLogModuleSupplier = ::CustomerLogModuleImpl,
    private val nativeModuleSupplier: NativeModuleSupplier = ::NativeModuleImpl,
    private val dataContainerModuleSupplier: DataContainerModuleSupplier = ::DataContainerModuleImpl,
    private val dataSourceModuleSupplier: DataSourceModuleSupplier = ::DataSourceModuleImpl,
    private val sessionModuleSupplier: SessionModuleSupplier = ::SessionModuleImpl,
    private val crashModuleSupplier: CrashModuleSupplier = ::CrashModuleImpl,
    private val payloadModuleSupplier: PayloadModuleSupplier = ::PayloadModuleImpl,
) {
    lateinit var coreModule: CoreModule
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

    lateinit var sdkObservabilityModule: SdkObservabilityModule
        private set

    lateinit var customerLogModule: CustomerLogModule
        private set

    lateinit var nativeModule: NativeModule
        private set

    lateinit var dataContainerModule: DataContainerModule
        private set

    lateinit var dataSourceModule: DataSourceModule
        private set

    lateinit var sessionModule: SessionModule
        private set

    lateinit var crashModule: CrashModule
        private set

    lateinit var payloadModule: PayloadModule
        private set

    private val asyncInitTask = AtomicReference<Future<*>?>(null)

    @Volatile
    private var synchronousInitCompletionMs: Long = -1L

    @Volatile
    private var asyncInitCompletionMs: Long = -1L

    /**
     * Returns true when the call has triggered an initialization, false if initialization is already in progress or is complete.
     */
    @JvmOverloads
    fun init(
        context: Context,
        enableIntegrationTesting: Boolean,
        appFramework: AppFramework,
        sdkStartTimeMs: Long,
        customAppId: String? = null,
        configServiceProvider: Provider<ConfigService?> = { null },
        versionChecker: VersionChecker = BuildVersionChecker,
    ): Boolean {
        try {
            Systrace.startSynchronous("modules-init")
            if (isInitialized()) {
                return false
            }

            synchronized(asyncInitTask) {
                return if (!isInitialized()) {
                    coreModule = init(CoreModule::class) { coreModuleSupplier(context, appFramework) }
                    workerThreadModule = init(WorkerThreadModule::class) { workerThreadModuleSupplier(initModule) }

                    val initTask = postInit(OpenTelemetryModule::class) {
                        workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION).submit(TaskPriority.CRITICAL) {
                            Systrace.traceSynchronous("span-service-init") {
                                openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
                            }
                            asyncInitCompletionMs = initModule.clock.now()
                        }
                    }

                    val serviceRegistry = coreModule.serviceRegistry
                    postInit(OpenTelemetryModule::class) {
                        serviceRegistry.registerService(initModule.telemetryService)
                        serviceRegistry.registerService(openTelemetryModule.spanService)
                    }

                    systemServiceModule = init(SystemServiceModule::class) {
                        systemServiceModuleSupplier(coreModule, versionChecker)
                    }

                    androidServicesModule = init(AndroidServicesModule::class) {
                        androidServicesModuleSupplier(initModule, coreModule, workerThreadModule)
                    }
                    postInit(AndroidServicesModule::class) {
                        serviceRegistry.registerService(androidServicesModule.preferencesService)
                    }

                    storageModule = init(StorageModule::class) {
                        storageModuleSupplier(initModule, coreModule, workerThreadModule)
                    }

                    essentialServiceModule = init(EssentialServiceModule::class) {
                        essentialServiceModuleSupplier(
                            initModule,
                            coreModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            storageModule,
                            customAppId,
                            enableIntegrationTesting,
                            configServiceProvider
                        )
                    }
                    postInit(EssentialServiceModule::class) {
                        serviceRegistry.registerServices(
                            essentialServiceModule.processStateService,
                            essentialServiceModule.metadataService,
                            essentialServiceModule.configService,
                            essentialServiceModule.activityLifecycleTracker,
                            essentialServiceModule.networkConnectivityService,
                            essentialServiceModule.userService
                        )

                        val networkBehavior = essentialServiceModule.configService.networkBehavior
                        if (networkBehavior.isNativeNetworkingMonitoringEnabled()) {
                            registerFactory(networkBehavior.isRequestContentLengthCaptureEnabled())
                        }

                        // only call after ConfigService has initialized.
                        essentialServiceModule.metadataService.precomputeValues()
                    }

                    dataCaptureServiceModule = init(DataCaptureServiceModule::class) {
                        dataCaptureServiceModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            coreModule,
                            systemServiceModule,
                            essentialServiceModule,
                            workerThreadModule,
                            versionChecker
                        )
                    }

                    Systrace.traceSynchronous("startup-tracker") {
                        coreModule.application.registerActivityLifecycleCallbacks(
                            dataCaptureServiceModule.startupTracker
                        )
                    }

                    postInit(DataCaptureServiceModule::class) {
                        serviceRegistry.registerServices(
                            dataCaptureServiceModule.webviewService,
                            dataCaptureServiceModule.memoryService,
                            dataCaptureServiceModule.componentCallbackService,
                            dataCaptureServiceModule.powerSaveModeService,
                            dataCaptureServiceModule.breadcrumbService,
                            dataCaptureServiceModule.pushNotificationService,
                            dataCaptureServiceModule.thermalStatusService
                        )
                    }

                    deliveryModule = init(DeliveryModule::class) {
                        deliveryModuleSupplier(coreModule, workerThreadModule, storageModule, essentialServiceModule)
                    }

                    postInit(DeliveryModule::class) {
                        serviceRegistry.registerService(deliveryModule.deliveryService)
                    }

                    /** Since onForeground() is called sequential in the order that services registered for it,
                     * it is important to initialize the `EmbraceAnrService`, and thus register the `onForeground()
                     * listener for it, before the `EmbraceSessionService`.
                     * The onForeground() call inside the EmbraceAnrService should be called before the
                     * EmbraceSessionService call. This is necessary since the EmbraceAnrService should be able to
                     * force a Main thread health check and close the pending ANR intervals that happened on the
                     * background before the next session is created.
                     * */
                    anrModule = init(AnrModule::class) {
                        anrModuleSupplier(initModule, coreModule, essentialServiceModule, workerThreadModule)
                    }

                    postInit(AnrModule::class) {
                        serviceRegistry.registerServices(
                            anrModule.anrService,
                            anrModule.responsivenessMonitorService
                        )

                        // set callbacks and pass in non-placeholder config.
                        anrModule.anrService.finishInitialization(
                            essentialServiceModule.configService
                        )
                    }

                    // initialize the logger early so that logged exceptions have a good chance of
                    // being appended to the exceptions service rather than logcat
                    sdkObservabilityModule = init(SdkObservabilityModule::class) {
                        sdkObservabilityModuleSupplier(initModule, essentialServiceModule)
                    }

                    postInit(SdkObservabilityModule::class) {
                        serviceRegistry.registerService(sdkObservabilityModule.internalErrorService)
                        InternalStaticEmbraceLogger.logger.addLoggerAction(sdkObservabilityModule.internalErrorLogger)
                    }

                    val sessionProperties = Systrace.traceSynchronous("session-properties-init") {
                        EmbraceSessionProperties(
                            androidServicesModule.preferencesService,
                            essentialServiceModule.configService,
                            coreModule.logger
                        )
                    }

                    nativeModule = init(NativeModule::class) {
                        nativeModuleSupplier(
                            coreModule,
                            storageModule,
                            essentialServiceModule,
                            deliveryModule,
                            androidServicesModule,
                            sessionProperties,
                            workerThreadModule
                        )
                    }

                    postInit(NativeModule::class) {
                        val ndkService = nativeModule.ndkService
                        val initWorkerTaskQueueTime = initModule.clock.now()
                        workerThreadModule.backgroundWorker(WorkerName.SERVICE_INIT).submit {
                            val taskRuntimeMs = initModule.clock.now()
                            dataCaptureServiceModule.appStartupTraceEmitter.addTrackedInterval(
                                name = "init-worker-schedule-delay",
                                startTimeMs = initWorkerTaskQueueTime,
                                endTimeMs = taskRuntimeMs
                            )
                        }
                        serviceRegistry.registerServices(
                            ndkService,
                            nativeModule.nativeThreadSamplerService
                        )

                        if (essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled()) {
                            essentialServiceModule.sessionIdTracker.ndkService = nativeModule.ndkService
                        }

                        if (nativeModule.nativeThreadSamplerInstaller != null) {
                            // install the native thread sampler
                            nativeModule.nativeThreadSamplerService?.let { nativeThreadSamplerService ->
                                nativeThreadSamplerService.setupNativeSampler()

                                // In Unity this should always run on the Unity thread.
                                if (coreModule.appFramework == AppFramework.UNITY && isUnityMainThread()) {
                                    try {
                                        if (nativeModule.nativeThreadSamplerInstaller != null) {
                                            nativeModule.nativeThreadSamplerInstaller?.monitorCurrentThread(
                                                nativeThreadSamplerService,
                                                essentialServiceModule.configService,
                                                anrModule.anrService
                                            )
                                        } else {
                                            InternalStaticEmbraceLogger.logger.logWarning(
                                                "nativeThreadSamplerInstaller not started, cannot sample current thread"
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        InternalStaticEmbraceLogger.logger.logError("Failed to sample current thread during ANRs", t)
                                    }
                                }
                            }
                        }
                    }

                    payloadModule = init(PayloadModule::class) {
                        payloadModuleSupplier(
                            essentialServiceModule,
                            coreModule,
                            androidServicesModule,
                            systemServiceModule,
                            workerThreadModule,
                            nativeModule,
                            openTelemetryModule,
                            sdkObservabilityModule
                        )
                    }

                    customerLogModule = init(CustomerLogModule::class) {
                        customerLogModuleSupplier(
                            initModule,
                            coreModule,
                            openTelemetryModule,
                            androidServicesModule,
                            essentialServiceModule,
                            deliveryModule,
                            sessionProperties,
                            workerThreadModule,
                            payloadModule
                        )
                    }

                    postInit(CustomerLogModule::class) {
                        serviceRegistry.registerServices(
                            customerLogModule.logMessageService,
                            customerLogModule.networkCaptureService,
                            customerLogModule.networkLoggingService
                        )
                        // Start the log orchestrator
                        customerLogModule.logOrchestrator
                    }

                    dataContainerModule = init(DataContainerModule::class) {
                        dataContainerModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            coreModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            essentialServiceModule,
                            dataCaptureServiceModule,
                            anrModule,
                            customerLogModule,
                            deliveryModule,
                            nativeModule,
                            sessionProperties,
                            sdkStartTimeMs
                        )
                    }

                    postInit(NativeModule::class) {
                        serviceRegistry.registerServices(
                            dataContainerModule.performanceInfoService,
                            dataContainerModule.eventService,
                            dataContainerModule.applicationExitInfoService
                        )
                    }

                    dataSourceModule = init(DataSourceModule::class) {
                        dataSourceModuleSupplier(
                            essentialServiceModule,
                            initModule,
                            openTelemetryModule,
                            systemServiceModule,
                            androidServicesModule,
                            workerThreadModule
                        )
                    }

                    sessionModule = init(SessionModule::class) {
                        sessionModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            androidServicesModule,
                            essentialServiceModule,
                            nativeModule,
                            dataContainerModule,
                            deliveryModule,
                            sessionProperties,
                            dataCaptureServiceModule,
                            customerLogModule,
                            sdkObservabilityModule,
                            workerThreadModule,
                            dataSourceModule,
                            payloadModule
                        )
                    }

                    crashModule = init(CrashModule::class) {
                        crashModuleSupplier(
                            initModule,
                            storageModule,
                            essentialServiceModule,
                            deliveryModule,
                            nativeModule,
                            sessionModule,
                            anrModule,
                            dataContainerModule,
                            androidServicesModule
                        )
                    }

                    postInit(CrashModule::class) {
                        Thread.setDefaultUncaughtExceptionHandler(crashModule.automaticVerificationExceptionHandler)
                        serviceRegistry.registerService(crashModule.crashService)
                    }

                    // Sets up the registered services. This method is called after the SDK has been started and no more services can
                    // be added to the registry. It sets listeners for any services that were registered.
                    serviceRegistry.closeRegistration()
                    serviceRegistry.registerActivityListeners(essentialServiceModule.processStateService)
                    serviceRegistry.registerConfigListeners(essentialServiceModule.configService)
                    serviceRegistry.registerMemoryCleanerListeners(essentialServiceModule.memoryCleanerService)
                    serviceRegistry.registerActivityLifecycleListeners(essentialServiceModule.activityLifecycleTracker)

                    asyncInitTask.set(initTask)
                    synchronousInitCompletionMs = initModule.clock.now()
                    true
                } else {
                    false
                }
            }
        } finally {
            Systrace.endSynchronous()
        }
    }

    /**
     * A blocking get that returns when the async portion of initialization is complete. An exception will be thrown by the underlying
     * [Future] if there is a timeout or if this failed for other reasons.
     */
    @JvmOverloads
    fun waitForAsyncInit(timeout: Long = 5L, unit: TimeUnit = TimeUnit.SECONDS) {
        Systrace.traceSynchronous("async-init-wait") {
            asyncInitTask.get()?.get(timeout, unit)
        }

        Systrace.traceSynchronous("record-delay") {
            // If async init finished after synchronous init, there's a delay so record that delay
            // Otherwise, record a 0-duration span to signify there was no significant wait
            val delayStartMs = synchronousInitCompletionMs
            val delayEndMs = max(synchronousInitCompletionMs, asyncInitCompletionMs)
            if (delayStartMs > 0) {
                dataCaptureServiceModule.appStartupTraceEmitter.addTrackedInterval(
                    name = "async-init-delay",
                    startTimeMs = delayStartMs,
                    endTimeMs = delayEndMs
                )
            }
        }
    }

    fun stopServices() {
        if (isInitialized()) {
            return
        }

        synchronized(asyncInitTask) {
            if (isInitialized()) {
                InternalStaticEmbraceLogger.logger.logDeveloper("Embrace", "Attempting to close services...")
                coreModule.serviceRegistry.close()
                InternalStaticEmbraceLogger.logger.logDeveloper("Embrace", "Services closed")
                workerThreadModule.close()
                essentialServiceModule.processStateService.close()
            } else {
                asyncInitTask.set(null)
            }
        }
    }

    private fun isInitialized(): Boolean = asyncInitTask.get() != null

    private fun <T> init(module: KClass<*>, provider: Provider<T>): T =
        Systrace.traceSynchronous("${toSectionName(module)}-init") { provider() }

    private fun <T> postInit(module: KClass<*>, code: () -> T): T =
        Systrace.traceSynchronous("${toSectionName(module)}-post-init") { code() }

    // This is called twice for each input - memoizing/caching is not worth the hassle
    private fun toSectionName(klass: KClass<*>): String =
        klass.simpleName?.removeSuffix("Module")?.toLowerCase(Locale.ENGLISH) ?: "module"
}
