@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.content.Context
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import io.embrace.android.embracesdk.internal.storage.StatFsAvailabilityChecker
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.AndroidSectionRecorder
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.TimeUnit

/**
 * Constructs objects used by the initialized SDK. Throws [SdkDisabledException] if the SDK
 * is disabled.
 *
 * Each supplier is a test seam and is null in production. Where it is null the real construction
 * below runs instead; because [init] is inlined that costs no lambda allocation on the default path.
 */
@Suppress("UseCheckOrError")
internal class InitializedModuleGraph(
    context: Context,
    preStartServices: PreStartServices,
    val sdkStartTimeMs: Long,
    val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    versionChecker: VersionChecker = BuildVersionChecker,
    private val initModuleSupplier: InitModuleSupplier? = null,
    private val openTelemetryModuleSupplier: OpenTelemetryModuleSupplier? = null,
    private val coreModuleSupplier: CoreModuleSupplier? = null,
    private val configServiceSupplier: ConfigServiceSupplier? = null,
    private val workerThreadPoolSupplier: WorkerThreadPoolSupplier? = null,
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

    init {
        SystemTrace.recorder = AndroidSectionRecorder
    }

    val initModule: InitModule = EmbTrace.trace(sectionName = "bootstrapper-init", recordDuration = true) {
        initModuleSupplier?.invoke(preStartServices) ?: EmbTrace.trace("init-module") {
            InitModuleImpl(preStartServices)
        }
    }

    val openTelemetryModule: OpenTelemetryModule = EmbTrace.trace("otel-module") {
        openTelemetryModuleSupplier?.invoke(initModule, preStartServices.externalOtelConfig)
            ?: OpenTelemetryModuleImpl(initModule, preStartServices.externalOtelConfig)
    }

    private val keyValueStore: Lazy<KeyValueStore> = lazy { createKeyValueStore(context, initModule.jsonSerializer) }

    private val persistedConfig: PersistedConfig = EmbTrace.trace(
        sectionName = "persisted-config-load",
        recordDuration = true,
    ) {
        PersistedConfig(
            serializer = initModule.jsonSerializer,
            filesDir = context.filesDir,
            instrumentedConfig = initModule.instrumentedConfig,
            keyValueStore = keyValueStore,
            uuidSource = initModule.uuidSource,
            clock = initModule.clock,
        )
    }.also {
        openTelemetryModule.setOtelBehavior(it.otelBehavior)
    }

    val workerThreadPool: WorkerThreadPool = EmbTrace.trace("workerthread-init") {
        workerThreadPoolSupplier?.invoke() ?: WorkerThreadPoolImpl()
    }.also {
        prewarmSharedPreferences(context, it)
    }

    val coreModule: CoreModule = init("core") {
        coreModuleSupplier?.invoke(context, initModule, keyValueStore)
            ?: CoreModuleImpl(context, initModule, keyValueStore)
    }

    val configService: ConfigService = init("config") {
        configServiceSupplier?.invoke(
            initModule,
            preStartServices,
            workerThreadPool,
            persistedConfig,
        ) ?: EmbTrace.trace(sectionName = "config-service-init", recordDuration = true) {
            createConfigService(initModule, preStartServices, workerThreadPool, persistedConfig)
        }
    }.apply {
        // kick off config HTTP request early so the SDK can't get in a permanently disabled state.
        scheduleConfigRequests()
        EmbTrace.trace("sdk-disable-check") {
            EmbTrace.trace("behavior-check") {
                if (config.sdkMode.sdkDisabled) {
                    // bail out early. Caught at a higher-level that relies on this specific type
                    throw SdkDisabledException()
                }
            }
        }
        // Deliberately after the disable check so a disabled SDK never builds the OTel SDK, and after
        // configService so that the otel behavior set when persistedConfig was loaded has been read
        // from the same persisted config this service uses.
        EmbTrace.trace(sectionName = "span-service-init", recordDuration = true) {
            openTelemetryModule.currentSessionPartSpan.initializeService(sdkStartTimeMs)
        }
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

    /**
     * Touches the default `SharedPreferences` on a background worker as early as possible to speculatively
     * attempt to reduce the amount of time Android blocks with `awaitLoadedLocked` later on in SDK init.
     */
    private fun prewarmSharedPreferences(context: Context, workerThreadPool: WorkerThreadPool) {
        workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker).submit {
            try {
                PreferenceManager.getDefaultSharedPreferences(context)
            } catch (ignored: Throwable) {
            }
        }
    }
}
