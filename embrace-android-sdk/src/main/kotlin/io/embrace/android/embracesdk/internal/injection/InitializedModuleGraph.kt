package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.content.Context
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import io.embrace.android.embracesdk.internal.storage.StatFsAvailabilityChecker
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.TimeUnit

/**
 * Constructs objects used by the initialized SDK. Only constructed once [ConfigLoadServices] has
 * confirmed the SDK is not disabled.
 *
 * Each supplier is a test seam and is null in production. Where it is null the real construction
 * below runs instead; because [init] is inlined that costs no lambda allocation on the default path.
 */
internal class InitializedModuleGraph(
    context: Context,
    preStartServices: PreStartServices,
    configLoadServices: ConfigLoadServices,
    val sdkStartTimeMs: Long,
    val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    versionChecker: VersionChecker = BuildVersionChecker,
    private val initModuleSupplier: InitModuleSupplier? = null,
    private val openTelemetryModuleSupplier: OpenTelemetryModuleSupplier? = null,
    private val coreModuleSupplier: CoreModuleSupplier? = null,
    private val storageServiceSupplier: StorageServiceSupplier? = null,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier? = null,
    private val featureModuleSupplier: FeatureModuleSupplier? = null,
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier? = null,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier? = null,
    private val deliveryModuleSupplier: DeliveryModuleSupplier? = null,
    private val threadBlockageServiceSupplier: ThreadBlockageServiceSupplier? = null,
    private val logModuleSupplier: LogModuleSupplier? = null,
    private val userSessionOrchestrationModuleSupplier: UserSessionOrchestrationModuleSupplier? = null,
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier? = null,
) {

    val initModule: InitModule = EmbTrace.trace(sectionName = "bootstrapper-init", recordDuration = true) {
        initModuleSupplier?.invoke(preStartServices) ?: EmbTrace.trace("init-module") {
            InitModuleImpl(
                preStartServices = preStartServices,
                uuidSource = configLoadServices.uuidSource,
                instrumentedConfig = configLoadServices.instrumentedConfig,
                jsonSerializer = configLoadServices.jsonSerializer,
                okHttpClient = configLoadServices.okHttpClient,
            )
        }
    }

    val workerThreadPool: WorkerThreadPool = configLoadServices.workerThreadPool
    val configService: ConfigService = configLoadServices.configService

    val openTelemetryModule: OpenTelemetryModule = EmbTrace.trace("otel-module") {
        openTelemetryModuleSupplier?.invoke(initModule, preStartServices.externalOtelConfig)
            ?: OpenTelemetryModuleImpl(initModule, preStartServices.externalOtelConfig)
    }.apply {
        // the persisted config decides which OTel SDK implementation is built, so it is applied
        // before anything below can build the OTel SDK
        setOtelBehavior(configLoadServices.persistedConfig.otelBehavior)
        EmbTrace.trace(sectionName = "span-service-init", recordDuration = true) {
            currentSessionPartSpan.initializeService(sdkStartTimeMs)
        }
    }

    val coreModule: CoreModule = init("core") {
        coreModuleSupplier?.invoke(context, initModule, configLoadServices.keyValueStore)
            ?: CoreModuleImpl(context, initModule, configLoadServices.keyValueStore)
    }

    val essentialServiceModule: EssentialServiceModule =
        init(name = "essential-service", recordDuration = true) {
            val lifecycleTrackerProvider: Provider<LifecycleTracker?> = { null }
            val networkConnectivityServiceProvider: Provider<NetworkConnectivityService?> = { null }
            val sessionOrchestratorProvider = { userSessionOrchestrationModule.sessionOrchestrator }

            essentialServiceModuleSupplier?.invoke(
                initModule,
                configService,
                openTelemetryModule,
                coreModule,
                workerThreadPool,
                context,
                lifecycleTrackerProvider,
                networkConnectivityServiceProvider,
                sessionOrchestratorProvider,
            ) ?: EssentialServiceModuleImpl(
                initModule,
                configService,
                openTelemetryModule,
                coreModule,
                workerThreadPool,
                context,
                lifecycleTrackerProvider,
                networkConnectivityServiceProvider,
                sessionOrchestratorProvider,
            )
        }

    val storageService: StorageService = init("storage") {
        storageServiceSupplier?.invoke(initModule, coreModule, workerThreadPool)
            ?: EmbraceStorageService(
                coreModule.context,
                initModule.telemetryService,
                StatFsAvailabilityChecker(coreModule.context),
            ).also { storageService ->
                workerThreadPool
                    .backgroundWorker(Worker.Background.IoRegWorker)
                    .schedule<Unit>({ storageService.logStorageTelemetry() }, 1, TimeUnit.MINUTES)
            }
    }

    val instrumentationModule: InstrumentationModule = init(name = "instrumentation", recordDuration = true) {
        // Forward references: break the instrumentation <-> userSessionOrchestration cycle.
        val userSessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider.getCurrentUserSessionId() }
        val activeSessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider.getActiveSessionIds() }

        instrumentationModuleSupplier?.invoke(
            initModule,
            openTelemetryModule,
            workerThreadPool,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
            userSessionIdsProvider,
            activeSessionIdsProvider,
        ) ?: InstrumentationModuleImpl(
            initModule,
            openTelemetryModule,
            workerThreadPool,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
            userSessionIdsProvider,
            activeSessionIdsProvider,
        )
    }

    val featureModule: FeatureModule = init("feature") {
        featureModuleSupplier?.invoke(
            instrumentationModule,
            storageService,
        ) ?: FeatureModuleImpl(
            instrumentationModule = instrumentationModule,
            storageService = storageService,
        )
    }

    val dataCaptureServiceModule: DataCaptureServiceModule = init("data-capture-service") {
        val instrumentationArgs = instrumentationModule.instrumentationArgs
        val destination = instrumentationArgs.destination
        val activityManager = instrumentationArgs.systemService<ActivityManager>(Context.ACTIVITY_SERVICE)
        val backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker)
        dataCaptureServiceModuleSupplier?.invoke(
            initModule.clock,
            initModule.logger,
            destination,
            configService,
            { coreModule.appVersionStartupCounter },
            initModule.startupClassifier,
            versionChecker,
            activityManager,
            backgroundWorker,
        ) ?: DataCaptureServiceModuleImpl(
            initModule.clock,
            initModule.logger,
            destination,
            configService,
            { coreModule.appVersionStartupCounter },
            initModule.startupClassifier,
            versionChecker,
            activityManager,
            backgroundWorker,
        )
    }

    val deliveryModule: DeliveryModule? = init(name = "delivery", recordDuration = true) {
        if (configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            deliveryModuleSupplier?.invoke(
                configService,
                initModule,
                openTelemetryModule,
                workerThreadPool,
                coreModule,
                essentialServiceModule,
                null,
                null,
                null,
                null,
            ) ?: DeliveryModuleImpl(
                configService = configService,
                initModule = initModule,
                otelModule = openTelemetryModule,
                workerThreadPool = workerThreadPool,
                coreModule = coreModule,
                essentialServiceModule = essentialServiceModule,
                requestExecutionServiceProvider = null,
                payloadStorageServiceProvider = null,
                cacheStorageServiceProvider = null,
            )
        }
    }

    val threadBlockageService: ThreadBlockageService? = init("thread-blockage") {
        val args = instrumentationModule.instrumentationArgs
        // Only create the thread blockage service if the provider is null, not when the provider returns null
        when (threadBlockageServiceSupplier) {
            null -> createThreadBlockageService(args)
            else -> threadBlockageServiceSupplier.invoke(args)
        }
    }

    val payloadSourceModule: PayloadSourceModule = init(name = "payload-source", recordDuration = true) {
        payloadSourceModuleSupplier?.invoke(
            initModule,
            coreModule,
            workerThreadPool,
            essentialServiceModule,
            configService,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule,
        ) ?: PayloadSourceModuleImpl(
            initModule,
            coreModule,
            workerThreadPool,
            essentialServiceModule,
            configService,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule,
        )
    }

    val logModule: LogModule = init("log") {
        logModuleSupplier?.invoke(
            initModule,
            openTelemetryModule,
            essentialServiceModule,
            configService,
            deliveryModule,
            workerThreadPool,
            payloadSourceModule,
        ) ?: LogModuleImpl(
            initModule,
            openTelemetryModule,
            essentialServiceModule,
            configService,
            deliveryModule,
            workerThreadPool,
            payloadSourceModule,
        )
    }

    val userSessionOrchestrationModule: UserSessionOrchestrationModule =
        init(name = "user-session-orchestration", recordDuration = true) {
            val startupDurationProvider = dataCaptureServiceModule.startupService::getSdkStartupDuration
            val appVersionStartupCounterProvider = dataCaptureServiceModule.startupService::getAppVersionStartupCounter
            userSessionOrchestrationModuleSupplier?.invoke(
                initModule,
                openTelemetryModule,
                coreModule,
                essentialServiceModule,
                configService,
                deliveryModule,
                instrumentationModule,
                payloadSourceModule,
                startupDurationProvider,
                appVersionStartupCounterProvider,
                logModule,
                workerThreadPool,
            ) ?: UserSessionOrchestrationModuleImpl(
                initModule,
                openTelemetryModule,
                coreModule,
                essentialServiceModule,
                configService,
                deliveryModule,
                instrumentationModule,
                payloadSourceModule,
                startupDurationProvider,
                appVersionStartupCounterProvider,
                logModule,
                workerThreadPool,
            )
        }

    private inline fun <T> init(name: String, recordDuration: Boolean = false, supplier: () -> T): T =
        EmbTrace.trace("$name-init", recordDuration) { supplier() }
}
