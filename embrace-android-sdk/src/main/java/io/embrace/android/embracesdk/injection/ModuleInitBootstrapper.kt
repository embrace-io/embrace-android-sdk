package io.embrace.android.embracesdk.injection

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.anr.ndk.isUnityMainThread
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
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
import io.embrace.android.embracesdk.internal.utils.SessionModuleSupplier
import io.embrace.android.embracesdk.internal.utils.StorageModuleSupplier
import io.embrace.android.embracesdk.internal.utils.SystemServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.WorkerThreadModuleSupplier
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.ndk.NativeModuleImpl
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
    val logger: EmbLogger = EmbLoggerImpl(),
    val initModule: InitModule = InitModuleImpl(logger = logger),
    val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(initModule),
    private val coreModuleSupplier: CoreModuleSupplier = ::CoreModuleImpl,
    private val systemServiceModuleSupplier: SystemServiceModuleSupplier = ::SystemServiceModuleImpl,
    private val androidServicesModuleSupplier: AndroidServicesModuleSupplier = ::AndroidServicesModuleImpl,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier = ::WorkerThreadModuleImpl,
    private val storageModuleSupplier: StorageModuleSupplier = ::StorageModuleImpl,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier = ::EssentialServiceModuleImpl,
    private val dataSourceModuleSupplier: DataSourceModuleSupplier = ::DataSourceModuleImpl,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = ::DataCaptureServiceModuleImpl,
    private val deliveryModuleSupplier: DeliveryModuleSupplier = ::DeliveryModuleImpl,
    private val anrModuleSupplier: AnrModuleSupplier = ::AnrModuleImpl,
    private val customerLogModuleSupplier: CustomerLogModuleSupplier = ::CustomerLogModuleImpl,
    private val nativeModuleSupplier: NativeModuleSupplier = ::NativeModuleImpl,
    private val dataContainerModuleSupplier: DataContainerModuleSupplier = ::DataContainerModuleImpl,
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
                    coreModule = init(CoreModule::class) { coreModuleSupplier(context, appFramework, logger) }

                    val serviceRegistry = coreModule.serviceRegistry
                    postInit(InitModule::class) {
                        serviceRegistry.registerService(initModule.internalErrorService)
                    }
                    workerThreadModule = init(WorkerThreadModule::class) { workerThreadModuleSupplier(initModule) }

                    val initTask = postInit(OpenTelemetryModule::class) {
                        workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION).submit(TaskPriority.CRITICAL) {
                            Systrace.traceSynchronous("span-service-init") {
                                openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
                            }
                            asyncInitCompletionMs = initModule.clock.now()
                        }
                    }
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
                            openTelemetryModule,
                            coreModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            storageModule,
                            customAppId,
                            { customerLogModule },
                            { dataSourceModule },
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

                    anrModule = init(AnrModule::class) {
                        anrModuleSupplier(initModule, essentialServiceModule, workerThreadModule, openTelemetryModule)
                    }

                    dataSourceModule = init(DataSourceModule::class) {
                        dataSourceModuleSupplier(
                            initModule,
                            coreModule,
                            openTelemetryModule,
                            essentialServiceModule,
                            systemServiceModule,
                            androidServicesModule,
                            workerThreadModule,
                            anrModule
                        )
                    }
                    initModule.internalErrorService.internalErrorDataSource = { dataSourceModule.internalErrorDataSource.dataSource }

                    dataCaptureServiceModule = init(DataCaptureServiceModule::class) {
                        dataCaptureServiceModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            coreModule,
                            essentialServiceModule,
                            workerThreadModule,
                            versionChecker,
                            dataSourceModule
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
                            dataCaptureServiceModule.breadcrumbService,
                            dataCaptureServiceModule.pushNotificationService
                        )
                    }

                    deliveryModule = init(DeliveryModule::class) {
                        deliveryModuleSupplier(initModule, workerThreadModule, storageModule, essentialServiceModule)
                    }

                    postInit(DeliveryModule::class) {
                        serviceRegistry.registerService(deliveryModule.deliveryService)
                    }

                    postInit(AnrModule::class) {
                        serviceRegistry.registerServices(
                            anrModule.anrService
                        )

                        // set callbacks and pass in non-placeholder config.
                        anrModule.anrService.finishInitialization(
                            essentialServiceModule.configService
                        )
                    }

                    nativeModule = init(NativeModule::class) {
                        nativeModuleSupplier(
                            initModule,
                            coreModule,
                            storageModule,
                            essentialServiceModule,
                            deliveryModule,
                            androidServicesModule,
                            workerThreadModule
                        )
                    }

                    postInit(NativeModule::class) {
                        val ndkService = nativeModule.ndkService
                        val initWorkerTaskQueueTime = initModule.clock.now()
                        workerThreadModule.backgroundWorker(WorkerName.SERVICE_INIT).submit {
                            openTelemetryModule.spanService.recordCompletedSpan(
                                name = "init-worker-schedule-delay",
                                startTimeMs = initWorkerTaskQueueTime,
                                endTimeMs = initModule.clock.now(),
                                private = true,
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
                                            initModule.logger.logWarning(
                                                "nativeThreadSamplerInstaller not started, cannot sample current thread"
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        initModule.logger.logError("Failed to sample current thread during ANRs", t)
                                        logger.trackInternalError(InternalErrorType.NATIVE_THREAD_SAMPLE_FAIL, t)
                                    }
                                }
                            }
                        }
                    }

                    payloadModule = init(PayloadModule::class) {
                        payloadModuleSupplier(
                            initModule,
                            coreModule,
                            androidServicesModule,
                            essentialServiceModule,
                            systemServiceModule,
                            workerThreadModule,
                            nativeModule,
                            openTelemetryModule,
                            anrModule,
                            { sessionModule.sessionPropertiesService },
                            { dataCaptureServiceModule.webviewService }
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
                            workerThreadModule,
                            payloadModule
                        )
                    }

                    postInit(CustomerLogModule::class) {
                        serviceRegistry.registerServices(
                            customerLogModule.logService,
                            customerLogModule.networkCaptureService,
                            customerLogModule.networkLoggingService
                        )
                        // Start the log orchestrator
                        customerLogModule.logOrchestrator
                    }

                    dataContainerModule = init(DataContainerModule::class) {
                        dataContainerModuleSupplier(
                            initModule,
                            workerThreadModule,
                            essentialServiceModule,
                            deliveryModule,
                            sdkStartTimeMs
                        )
                    }

                    postInit(NativeModule::class) {
                        serviceRegistry.registerServices(
                            dataContainerModule.eventService,
                        )
                    }

                    sessionModule = init(SessionModule::class) {
                        sessionModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            androidServicesModule,
                            essentialServiceModule,
                            nativeModule,
                            deliveryModule,
                            workerThreadModule,
                            dataSourceModule,
                            payloadModule,
                            dataCaptureServiceModule,
                            dataContainerModule,
                            customerLogModule
                        )
                    }

                    workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION).submit {
                        sessionModule.sessionPropertiesService.populateCurrentSession()
                    }

                    crashModule = init(CrashModule::class) {
                        crashModuleSupplier(
                            initModule,
                            storageModule,
                            essentialServiceModule,
                            nativeModule,
                            sessionModule,
                            anrModule,
                            androidServicesModule,
                            customerLogModule,
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
                openTelemetryModule.spanService.recordCompletedSpan(
                    name = "async-init-delay",
                    startTimeMs = delayStartMs,
                    endTimeMs = delayEndMs,
                    private = true
                )
            }
        }
    }

    fun stopServices() {
        if (!isInitialized()) {
            return
        }

        synchronized(asyncInitTask) {
            if (isInitialized()) {
                coreModule.serviceRegistry.close()
                workerThreadModule.close()
                essentialServiceModule.processStateService.close()
                asyncInitTask.set(null)
            }
        }
    }

    fun isInitialized(): Boolean = asyncInitTask.get() != null

    private fun <T> init(module: KClass<*>, provider: Provider<T>): T =
        Systrace.traceSynchronous("${toSectionName(module)}-init") { provider() }

    private fun <T> postInit(module: KClass<*>, code: () -> T): T =
        Systrace.traceSynchronous("${toSectionName(module)}-post-init") { code() }

    // This is called twice for each input - memoizing/caching is not worth the hassle
    private fun toSectionName(klass: KClass<*>): String =
        klass.simpleName?.removeSuffix("Module")?.toLowerCase(Locale.ENGLISH) ?: "module"
}
