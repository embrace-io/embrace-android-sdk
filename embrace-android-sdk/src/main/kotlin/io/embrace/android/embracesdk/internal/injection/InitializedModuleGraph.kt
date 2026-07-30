package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
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
 * Constructed module dependencies that will be used by the initialized SDK.
 *
 * Each supplier is a test seam and is null in production. Where it is null the real construction
 * below runs instead; because [init] is inlined that costs no lambda allocation on the default path.
 */
@Suppress("UseCheckOrError")
internal class InitializedModuleGraph(
    context: Context,
    versionChecker: VersionChecker = BuildVersionChecker,
    override val initModule: InitModule,
    override val openTelemetryModule: OpenTelemetryModule,
    private val coreModuleSupplier: CoreModuleSupplier?,
    private val configServiceSupplier: ConfigServiceSupplier?,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier?,
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

    override val coreModule: CoreModule = init {
        coreModuleSupplier?.invoke(context, initModule) ?: CoreModuleImpl(context, initModule)
    }

    override val workerThreadModule: WorkerThreadModule = init {
        workerThreadModuleSupplier?.invoke() ?: WorkerThreadModuleImpl()
    }.apply {
        EmbTrace.trace("span-service-init") {
            openTelemetryModule.spanService.initializeService(coreModule.sdkStartTime)
        }
        EmbTrace.trace("event-service-init") {
            openTelemetryModule.eventService.initializeService(coreModule.sdkStartTime)
        }
    }

    override val configService: ConfigService = init {
        configServiceSupplier?.invoke(
            initModule,
            coreModule,
            openTelemetryModule,
            workerThreadModule,
        ) ?: EmbTrace.trace("config-service-init") {
            ConfigServiceImpl(
                instrumentedConfig = initModule.instrumentedConfig,
                worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                serializer = initModule.jsonSerializer,
                okHttpClient = initModule.okHttpClient,
                hasConfiguredOtlpExport = openTelemetryModule.otelSdkConfig::hasConfiguredOtlpExport,
                sdkVersion = BuildConfig.VERSION_NAME,
                apiLevel = Build.VERSION.SDK_INT,
                filesDir = coreModule.context.filesDir,
                store = coreModule.store,
                abis = Build.SUPPORTED_ABIS,
                logger = initModule.logger,
                uuidSource = initModule.uuidSource,
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
    }

    override val essentialServiceModule: EssentialServiceModule = init {
        val lifecycleOwnerProvider: Provider<LifecycleOwner?> = { null }
        val networkConnectivityServiceProvider: Provider<NetworkConnectivityService?> = { null }
        val sessionOrchestratorProvider = { userSessionOrchestrationModule.sessionOrchestrator }

        essentialServiceModuleSupplier?.invoke(
            initModule,
            configService,
            openTelemetryModule,
            coreModule,
            workerThreadModule,
            lifecycleOwnerProvider,
            networkConnectivityServiceProvider,
            sessionOrchestratorProvider,
        ) ?: EssentialServiceModuleImpl(
            initModule,
            configService,
            openTelemetryModule,
            coreModule,
            workerThreadModule,
            lifecycleOwnerProvider,
            networkConnectivityServiceProvider,
            sessionOrchestratorProvider,
        )
    }

    override val storageService: StorageService = init {
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

    override val instrumentationModule: InstrumentationModule = init {
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

    override val featureModule: FeatureModule = init {
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

    override val dataCaptureServiceModule: DataCaptureServiceModule = init {
        val destination = instrumentationModule.instrumentationArgs.destination
        dataCaptureServiceModuleSupplier?.invoke(
            initModule.clock,
            initModule.logger,
            destination,
            configService,
            initModule.startupClassifier,
            versionChecker,
        ) ?: DataCaptureServiceModuleImpl(
            initModule.clock,
            initModule.logger,
            destination,
            configService,
            initModule.startupClassifier,
            versionChecker,
        )
    }

    override val deliveryModule: DeliveryModule? = init {
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

    override val threadBlockageService: ThreadBlockageService? = init {
        val args = instrumentationModule.instrumentationArgs
        threadBlockageServiceSupplier?.invoke(args) ?: createThreadBlockageService(args)
    }

    override val payloadSourceModule: PayloadSourceModule = init {
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

    override val logModule: LogModule = init {
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

    override val userSessionOrchestrationModule: UserSessionOrchestrationModule = init {
        val startupDurationProvider = dataCaptureServiceModule.startupService::getSdkStartupDuration
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
            logModule,
            workerThreadModule,
        )
    }

    private inline fun <reified T> init(supplier: () -> T): T {
        val module = T::class
        val name = module.simpleName?.removeSuffix("Module")?.lowercase() ?: "module"
        return EmbTrace.trace("$name-init") { supplier() }
    }
}
