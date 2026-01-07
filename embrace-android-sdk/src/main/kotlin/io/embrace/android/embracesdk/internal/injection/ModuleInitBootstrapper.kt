package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
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
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    override val initModule: InitModule = EmbTrace.trace("init-module", ::InitModuleImpl),
    override val openTelemetryModule: OpenTelemetryModule = EmbTrace.trace("otel-module") {
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
    private val configServiceSupplier: ConfigServiceSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
            openTelemetryModule: OpenTelemetryModule,
            workerThreadModule: WorkerThreadModule,
        ->
        EmbTrace.trace("config-service-init") {
            ConfigServiceImpl(
                instrumentedConfig = initModule.instrumentedConfig,
                worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                serializer = initModule.jsonSerializer,
                okHttpClient = initModule.okHttpClient,
                hasConfiguredOtelExporters = openTelemetryModule.otelSdkConfig::hasConfiguredOtelExporters,
                sdkVersion = BuildConfig.VERSION_NAME,
                apiLevel = Build.VERSION.SDK_INT,
                filesDir = coreModule.context.filesDir,
                store = coreModule.store,
                abis = Build.SUPPORTED_ABIS,
                logger = initModule.logger,
            )
        }
    },
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier = { WorkerThreadModuleImpl() },
    private val storageServiceSupplier: StorageServiceSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
            workerThreadModule: WorkerThreadModule,
        ->
        val storageService = EmbraceStorageService(
            coreModule.context,
            initModule.telemetryService,
            StatFsAvailabilityChecker(coreModule.context)
        )
        workerThreadModule
            .backgroundWorker(Worker.Background.IoRegWorker)
            .schedule<Unit>({ storageService.logStorageTelemetry() }, 1, TimeUnit.MINUTES)
        storageService
    },
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier = {
            initModule: InitModule,
            configService: ConfigService,
            openTelemetryModule: OpenTelemetryModule,
            coreModule: CoreModule,
            workerThreadModule: WorkerThreadModule,
            lifecycleOwnerProvider: Provider<LifecycleOwner?>,
            networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
        ->
        EssentialServiceModuleImpl(
            initModule,
            configService,
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
            storageService: StorageService,
        ->
        FeatureModuleImpl(
            instrumentationModule = instrumentationModule,
            configService = configService,
            storageService = storageService,
        )
    },
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier = {
            initModule: InitModule,
            openTelemetryModule: OpenTelemetryModule,
            workerThreadModule: WorkerThreadModule,
            configService: ConfigService,
            essentialServiceModule: EssentialServiceModule,
            coreModule: CoreModule,
            storageService: StorageService,
        ->
        InstrumentationModuleImpl(
            initModule,
            openTelemetryModule,
            workerThreadModule,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
        )
    },
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = {
            clock: Clock,
            logger: InternalLogger,
            destination: TelemetryDestination,
            configService: ConfigService,
            versionChecker: VersionChecker,
        ->
        DataCaptureServiceModuleImpl(
            clock,
            logger,
            destination,
            configService,
            versionChecker
        )
    },
    private val deliveryModuleSupplier: DeliveryModuleSupplier = {
            configService: ConfigService,
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
            configService,
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
    private val threadBlockageServiceSupplier: ThreadBlockageServiceSupplier = { args: InstrumentationArgs ->
        createThreadBlockageService(args)
    },
    private val logModuleSupplier: LogModuleSupplier = {
            initModule: InitModule,
            openTelemetryModule: OpenTelemetryModule,
            essentialServiceModule: EssentialServiceModule,
            configService: ConfigService,
            deliveryModule: DeliveryModule?,
            workerThreadModule: WorkerThreadModule,
            payloadSourceModule: PayloadSourceModule,
        ->
        LogModuleImpl(
            initModule,
            openTelemetryModule,
            essentialServiceModule,
            configService,
            deliveryModule,
            workerThreadModule,
            payloadSourceModule,
        )
    },
    private val sessionOrchestratorSupplier: SessionOrchestratorSupplier = {
            initModule: InitModule,
            openTelemetryModule: OpenTelemetryModule,
            coreModule: CoreModule,
            essentialServiceModule: EssentialServiceModule,
            configService: ConfigService,
            deliveryModule: DeliveryModule?,
            instrumentationModule: InstrumentationModule,
            payloadSourceModule: PayloadSourceModule,
            startupDurationProvider: () -> Long?,
            logModule: LogModule,
        ->
        createSessionOrchestrator(
            initModule,
            openTelemetryModule,
            coreModule,
            essentialServiceModule,
            configService,
            deliveryModule,
            instrumentationModule,
            payloadSourceModule,
            startupDurationProvider,
            logModule
        )
    },
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier = {
            initModule: InitModule,
            coreModule: CoreModule,
            workerThreadModule: WorkerThreadModule,
            essentialServiceModule: EssentialServiceModule,
            configService: ConfigService,
            otelModule: OpenTelemetryModule,
            otelPayloadMapper: OtelPayloadMapper?,
            deliveryModule: DeliveryModule?,
        ->
        PayloadSourceModuleImpl(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configService,
            otelModule,
            otelPayloadMapper,
            deliveryModule,
        )
    },
) : ModuleGraph {

    @Volatile
    private var delegate: ModuleGraph = UninitializedModuleGraph
    override val coreModule: CoreModule get() = delegate.coreModule
    override val configService: ConfigService get() = delegate.configService
    override val workerThreadModule: WorkerThreadModule get() = delegate.workerThreadModule
    override val storageService: StorageService get() = delegate.storageService
    override val essentialServiceModule: EssentialServiceModule get() = delegate.essentialServiceModule
    override val dataCaptureServiceModule: DataCaptureServiceModule get() = delegate.dataCaptureServiceModule
    override val deliveryModule: DeliveryModule? get() = delegate.deliveryModule
    override val threadBlockageService: ThreadBlockageService? get() = delegate.threadBlockageService
    override val logModule: LogModule get() = delegate.logModule
    override val instrumentationModule: InstrumentationModule get() = delegate.instrumentationModule
    override val featureModule: FeatureModule get() = delegate.featureModule
    override val sessionOrchestrator: SessionOrchestrator get() = delegate.sessionOrchestrator
    override val payloadSourceModule: PayloadSourceModule get() = delegate.payloadSourceModule

    /**
     * Returns true when the call has triggered an initialization, false if initialization is already in progress or is complete.
     */
    fun init(
        context: Context,
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
                    versionChecker,
                    initModule,
                    openTelemetryModule,
                    coreModuleSupplier,
                    configServiceSupplier,
                    workerThreadModuleSupplier,
                    storageServiceSupplier,
                    essentialServiceModuleSupplier,
                    featureModuleSupplier,
                    instrumentationModuleSupplier,
                    dataCaptureServiceModuleSupplier,
                    deliveryModuleSupplier,
                    threadBlockageServiceSupplier,
                    logModuleSupplier,
                    sessionOrchestratorSupplier,
                    payloadSourceModuleSupplier
                )
                return isInitialized()
            }
        } catch (ignored: SdkDisabledException) {
            // do nothing - avoid instantiating SDK code any more than necessary.
            return false
        } finally {
            EmbTrace.end()
        }
    }

    fun stop() {
        if (!isInitialized()) {
            return
        }
        synchronized(delegate) {
            if (isInitialized()) {
                essentialServiceModule.networkConnectivityService.close()
                workerThreadModule.close()
                delegate = UninitializedModuleGraph
            }
        }
    }

    private fun isInitialized(): Boolean = delegate != UninitializedModuleGraph
}
