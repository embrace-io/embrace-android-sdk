package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
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
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.Locale
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    val initModule: InitModule = EmbTrace.trace("init-module") {
        InitModuleImpl(
            clock = NormalizedIntervalClock(),
            logger = EmbLoggerImpl(),
            systemInfo = SystemInfo()
        )
    },
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
            androidServicesModule: AndroidServicesModule,
        ->
        ConfigModuleImpl(
            initModule,
            coreModule,
            openTelemetryModule,
            workerThreadModule,
            androidServicesModule,
        )
    },
    private val systemServiceModuleSupplier: SystemServiceModuleSupplier = {
            coreModule: CoreModule,
            versionChecker: VersionChecker,
        ->
        SystemServiceModuleImpl(
            coreModule,
            versionChecker
        )
    },
    private val androidServicesModuleSupplier: AndroidServicesModuleSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
        ->
        AndroidServicesModuleImpl(
            initModule,
            coreModule
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
            systemServiceModule: SystemServiceModule,
            androidServicesModule: AndroidServicesModule,
            lifecycleOwnerProvider: Provider<LifecycleOwner?>,
            networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
        ->
        EssentialServiceModuleImpl(
            initModule,
            configModule,
            openTelemetryModule,
            coreModule,
            workerThreadModule,
            systemServiceModule,
            androidServicesModule,
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
            androidServicesModule: AndroidServicesModule,
            coreModule: CoreModule,
        ->
        InstrumentationModuleImpl(
            initModule,
            workerThreadModule,
            configModule,
            essentialServiceModule,
            androidServicesModule,
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
            androidServicesModule: AndroidServicesModule,
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
            androidServicesModule,
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
            coreModule: CoreModule,
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
            coreModule,
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
            androidServicesModule: AndroidServicesModule,
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
            androidServicesModule,
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
            systemServiceModule: SystemServiceModule,
            androidServicesModule: AndroidServicesModule,
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
            systemServiceModule,
            androidServicesModule,
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
        if (isInitialized()) {
            return false
        }
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
            serviceRegistry.registerService(lazy { configModule.configService.networkBehavior.domainCountLimiter })
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
                { null },
            )
        }
        postInit(EssentialServiceModule::class) {
            with(essentialServiceModule) {
                serviceRegistry.registerServices(
                    lazy { essentialServiceModule.appStateTracker },
                    lazy { activityLifecycleTracker },
                    lazy { networkConnectivityService }
                )

                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                    EmbTrace.trace("network-connectivity-registration") {
                        essentialServiceModule.networkConnectivityService.register()
                    }
                }
            }
        }

        instrumentationModule = init(InstrumentationModule::class) {
            instrumentationModuleSupplier(
                initModule,
                workerThreadModule,
                configModule,
                essentialServiceModule,
                androidServicesModule,
                coreModule,
            )
        }

        anrModule = init(AnrModule::class) {
            anrModuleSupplier(
                instrumentationModule.instrumentationArgs,
                essentialServiceModule.appStateTracker
            )
        }

        featureModule = init(FeatureModule::class) {
            featureModuleSupplier(
                instrumentationModule,
                configModule.configService,
                storageModule,
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
                { anrModule.anrOtelMapper },
                deliveryModule
            )
        }
        postInit(PayloadSourceModule::class) {
            payloadSourceModule.metadataService.precomputeValues()
        }

        nativeCoreModule = init(NativeCoreModule::class) {
            nativeCoreModuleSupplier(
                coreModule,
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

        nativeFeatureModule = init(NativeFeatureModule::class) {
            nativeFeatureModuleSupplier(
                nativeCoreModule,
                instrumentationModule,
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
                essentialServiceModule,
                configModule,
                deliveryModule,
                workerThreadModule,
                payloadSourceModule,
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
                instrumentationModule,
                payloadSourceModule,
                dataCaptureServiceModule.startupService,
                logModule
            )
        }
        postInit(SessionOrchestrationModule::class) {
            essentialServiceModule.telemetryDestination.sessionUpdateAction =
                sessionOrchestrationModule.sessionOrchestrator::onSessionDataUpdate
        }

        // Sets up the registered services. This method is called after the SDK has been started and no more services can
        // be added to the registry. It sets listeners for any services that were registered.
        EmbTrace.trace("service-registration") {
            serviceRegistry.closeRegistration()
            serviceRegistry.registerActivityListeners(essentialServiceModule.appStateTracker)
            serviceRegistry.registerMemoryCleanerListeners(sessionOrchestrationModule.memoryCleanerService)
            serviceRegistry.registerActivityLifecycleListeners(essentialServiceModule.activityLifecycleTracker)
        }
        return true
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

    private fun <T> init(module: KClass<*>, provider: Provider<T>): T =
        EmbTrace.trace("${toSectionName(module)}-init") { provider() }

    private fun <T> postInit(module: KClass<*>, code: () -> T): T =
        EmbTrace.trace("${toSectionName(module)}-post-init") { code() }

    // This is called twice for each input - memoizing/caching is not worth the hassle
    private fun toSectionName(klass: KClass<*>): String =
        klass.simpleName?.removeSuffix("Module")?.lowercase(Locale.ENGLISH) ?: "module"
}
