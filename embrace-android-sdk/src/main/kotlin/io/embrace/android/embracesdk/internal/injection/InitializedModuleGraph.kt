package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.os.Build
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.PersistedConfig
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
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.TimeUnit

/**
 * Constructed module dependencies that will be used by the initialized SDK.
 *
 * Each supplier is a test seam and is null in production. Where it is null the real construction
 * below runs instead; because [init] is inlined that costs no lambda allocation on the default path.
 */
@Suppress("UseCheckOrError")
internal class InitializedModuleGraph(
    context: Context,
    versionChecker: VersionChecker = BuildVersionChecker,
    override val sdkStartTimeMs: Long,
    override val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    override val initModule: InitModule,
    override val openTelemetryModule: OpenTelemetryModule,
    override val workerThreadModule: WorkerThreadModule,
    private val keyValueStore: Lazy<KeyValueStore>,
    private val persistedConfig: PersistedConfig,
    private val coreModuleSupplier: CoreModuleSupplier?,
    private val configServiceSupplier: ConfigServiceSupplier?,
    private val storageServiceSupplier: StorageServiceSupplier?,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier?,
    private val featureModuleSupplier: FeatureModuleSupplier?,
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier?,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier?,
    private val deliveryModuleSupplier: DeliveryModuleSupplier?,
    private val threadBlockageServiceSupplier: ThreadBlockageServiceSupplier?,
    private val logModuleSupplier: LogModuleSupplier?,
    private val userSessionOrchestrationModuleSupplier: UserSessionOrchestrationModuleSupplier?,
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier?,
) : ModuleGraph {

    override val coreModule: CoreModule = init("core") {
        coreModuleSupplier?.invoke(context, initModule, keyValueStore)
            ?: CoreModuleImpl(context, initModule, keyValueStore)
    }

    override val configService: ConfigService = init("config") {
        configServiceSupplier?.invoke(
            initModule,
            coreModule,
            openTelemetryModule,
            workerThreadModule,
            persistedConfig,
        ) ?: EmbTrace.trace(sectionName = "config-service-init", recordDuration = true) {
            ConfigServiceImpl(
                instrumentedConfig = initModule.instrumentedConfig,
                persistedConfig = persistedConfig,
                worker = workerThreadModule.backgroundWorker(Worker.Background.HttpRequestWorker),
                serializer = initModule.jsonSerializer,
                okHttpClient = initModule.okHttpClient,
                hasConfiguredOtlpExport = openTelemetryModule.otelSdkConfig::hasConfiguredOtlpExport,
                sdkVersion = BuildConfig.VERSION_NAME,
                apiLevel = Build.VERSION.SDK_INT,
                abis = Build.SUPPORTED_ABIS,
                logger = initModule.logger,
            )
        }
    }.apply {
        EmbTrace.trace("sdk-disable-check") {
            EmbTrace.trace("behavior-check") {
                if (sdkModeBehavior.isSdkDisabled()) {
                    // bail out early. Caught at a higher-level that relies on this specific type
                    throw SdkDisabledException()
                }
            }
        }
        // Deliberately after the disable check so a disabled SDK never builds the OTel SDK, and after
        // configService so that the otel behavior set in ModuleInitBootstrapper.init has been read
        // from the same persisted config this service uses.
        EmbTrace.trace(sectionName = "span-service-init", recordDuration = true) {
            openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
        }
    }

    override val essentialServiceModule: EssentialServiceModule =
        init(name = "essential-service", recordDuration = true) {
            val lifecycleTrackerProvider: Provider<LifecycleTracker?> = { null }
            val networkConnectivityServiceProvider: Provider<NetworkConnectivityService?> = { null }
            val sessionOrchestratorProvider = { userSessionOrchestrationModule.sessionOrchestrator }

            essentialServiceModuleSupplier?.invoke(
                initModule,
                configService,
                openTelemetryModule,
                coreModule,
                workerThreadModule,
                context,
                lifecycleTrackerProvider,
                networkConnectivityServiceProvider,
                sessionOrchestratorProvider,
            ) ?: EssentialServiceModuleImpl(
                initModule,
                configService,
                openTelemetryModule,
                coreModule,
                workerThreadModule,
                context,
                lifecycleTrackerProvider,
                networkConnectivityServiceProvider,
                sessionOrchestratorProvider,
            )
        }

    override val storageService: StorageService = init("storage") {
        storageServiceSupplier?.invoke(initModule, coreModule, workerThreadModule)
            ?: EmbraceStorageService(
                coreModule.context,
                initModule.telemetryService,
                StatFsAvailabilityChecker(coreModule.context),
            ).also { storageService ->
                workerThreadModule
                    .backgroundWorker(Worker.Background.IoRegWorker)
                    .schedule<Unit>({ storageService.logStorageTelemetry() }, 1, TimeUnit.MINUTES)
            }
    }

    override val instrumentationModule: InstrumentationModule = init(name = "instrumentation", recordDuration = true) {
        // Forward references: break the instrumentation <-> userSessionOrchestration cycle.
        val userSessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider.getCurrentUserSessionId() }
        val activeSessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider.getActiveSessionIds() }

        instrumentationModuleSupplier?.invoke(
            initModule,
            openTelemetryModule,
            workerThreadModule,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
            userSessionIdsProvider,
            activeSessionIdsProvider,
        ) ?: InstrumentationModuleImpl(
            initModule,
            openTelemetryModule,
            workerThreadModule,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
            userSessionIdsProvider,
            activeSessionIdsProvider,
        )
    }

    override val featureModule: FeatureModule = init("feature") {
        featureModuleSupplier?.invoke(
            instrumentationModule,
            configService,
            storageService,
        ) ?: FeatureModuleImpl(
            instrumentationModule = instrumentationModule,
            configService = configService,
            storageService = storageService,
        )
    }

    override val dataCaptureServiceModule: DataCaptureServiceModule = init("data-capture-service") {
        val destination = instrumentationModule.instrumentationArgs.destination
        dataCaptureServiceModuleSupplier?.invoke(
            initModule.clock,
            initModule.logger,
            destination,
            configService,
            { coreModule.appVersionStartupCounter },
            initModule.startupClassifier,
            versionChecker,
        ) ?: DataCaptureServiceModuleImpl(
            initModule.clock,
            initModule.logger,
            destination,
            configService,
            { coreModule.appVersionStartupCounter },
            initModule.startupClassifier,
            versionChecker,
        )
    }

    override val deliveryModule: DeliveryModule? = init(name = "delivery", recordDuration = true) {
        if (configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            deliveryModuleSupplier?.invoke(
                configService,
                initModule,
                openTelemetryModule,
                workerThreadModule,
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
                workerThreadModule = workerThreadModule,
                coreModule = coreModule,
                essentialServiceModule = essentialServiceModule,
                requestExecutionServiceProvider = null,
                payloadStorageServiceProvider = null,
                cacheStorageServiceProvider = null,
            )
        }
    }

    override val threadBlockageService: ThreadBlockageService? = init("thread-blockage") {
        val args = instrumentationModule.instrumentationArgs
        threadBlockageServiceSupplier?.invoke(args) ?: createThreadBlockageService(args)
    }

    override val payloadSourceModule: PayloadSourceModule = init(name = "payload-source", recordDuration = true) {
        payloadSourceModuleSupplier?.invoke(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configService,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule,
        ) ?: PayloadSourceModuleImpl(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configService,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule,
        )
    }

    override val logModule: LogModule = init("log") {
        logModuleSupplier?.invoke(
            initModule,
            openTelemetryModule,
            essentialServiceModule,
            configService,
            deliveryModule,
            workerThreadModule,
            payloadSourceModule,
        ) ?: LogModuleImpl(
            initModule,
            openTelemetryModule,
            essentialServiceModule,
            configService,
            deliveryModule,
            workerThreadModule,
            payloadSourceModule,
        )
    }

    override val userSessionOrchestrationModule: UserSessionOrchestrationModule =
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
                workerThreadModule,
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
                workerThreadModule,
            )
        }

    private inline fun <T> init(name: String, recordDuration: Boolean = false, supplier: () -> T): T =
        EmbTrace.trace("$name-init", recordDuration) { supplier() }
}
