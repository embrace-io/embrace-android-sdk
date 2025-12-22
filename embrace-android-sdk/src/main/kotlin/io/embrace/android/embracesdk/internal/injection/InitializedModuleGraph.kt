package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Constructed module dependencies that will be used by the initialized SDK.
 */
@Suppress("UseCheckOrError")
internal class InitializedModuleGraph(
    context: Context,
    versionChecker: VersionChecker = BuildVersionChecker,
    override val initModule: InitModule,
    override val openTelemetryModule: OpenTelemetryModule,
    private val coreModuleSupplier: CoreModuleSupplier,
    private val configModuleSupplier: ConfigModuleSupplier,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier,
    private val storageServiceSupplier: StorageServiceSupplier,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier,
    private val featureModuleSupplier: FeatureModuleSupplier,
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier,
    private val deliveryModuleSupplier: DeliveryModuleSupplier,
    private val threadBlockageServiceSupplier: ThreadBlockageServiceSupplier,
    private val logModuleSupplier: LogModuleSupplier,
    private val sessionOrchestratorSupplier: SessionOrchestratorSupplier,
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier,
) : ModuleGraph {

    override val coreModule: CoreModule = init {
        coreModuleSupplier(context, initModule)
    }

    override val workerThreadModule: WorkerThreadModule = init {
        workerThreadModuleSupplier()
    }.apply {
        EmbTrace.trace("span-service-init") {
            openTelemetryModule.spanService.initializeService(coreModule.sdkStartTime)
        }
    }

    override val configModule: ConfigModule = init {
        configModuleSupplier(
            initModule,
            coreModule,
            openTelemetryModule,
            workerThreadModule,
        )
    }.apply {
        EmbTrace.trace("sdk-disable-check") {
            // kick off config HTTP request first so the SDK can't get in a permanently disabled state
            EmbTrace.trace("load-config-response") {
                combinedRemoteConfigSource?.scheduleConfigRequests()
            }

            EmbTrace.trace("behavior-check") {
                if (configService.sdkModeBehavior.isSdkDisabled()) {
                    // bail out early. Caught at a higher-level that relies on this specific type
                    throw SdkDisabledException()
                }
            }
        }
    }

    override val storageService: StorageService = init {
        storageServiceSupplier(initModule, coreModule, workerThreadModule)
    }

    override val essentialServiceModule: EssentialServiceModule = init {
        essentialServiceModuleSupplier(
            initModule,
            configModule,
            openTelemetryModule,
            coreModule,
            workerThreadModule,
            { null },
            { null },
        )
    }

    override val instrumentationModule: InstrumentationModule = init {
        instrumentationModuleSupplier(
            initModule,
            openTelemetryModule,
            workerThreadModule,
            configModule,
            essentialServiceModule,
            coreModule,
            storageService,
        )
    }

    override val featureModule: FeatureModule = init {
        featureModuleSupplier(
            instrumentationModule,
            configModule.configService,
            storageService,
        )
    }

    override val dataCaptureServiceModule: DataCaptureServiceModule = init {
        dataCaptureServiceModuleSupplier(
            initModule.clock,
            initModule.logger,
            instrumentationModule.instrumentationArgs.destination,
            configModule.configService,
            versionChecker,
        )
    }

    override val deliveryModule: DeliveryModule = init {
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
    }

    override val threadBlockageService: ThreadBlockageService? = init {
        threadBlockageServiceSupplier(instrumentationModule.instrumentationArgs)
    }

    override val payloadSourceModule: PayloadSourceModule = init {
        payloadSourceModuleSupplier(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configModule,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule
        )
    }

    override val logModule: LogModule = init {
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

    override val sessionOrchestrator: SessionOrchestrator = init {
        sessionOrchestratorSupplier(
            initModule,
            openTelemetryModule,
            coreModule,
            essentialServiceModule,
            configModule,
            deliveryModule,
            instrumentationModule,
            payloadSourceModule,
            dataCaptureServiceModule.startupService::getSdkStartupDuration,
            logModule
        )
    }

    private inline fun <reified T> init(supplier: () -> T): T {
        val module = T::class
        val name = module.simpleName?.removeSuffix("Module")?.lowercase() ?: "module"
        return EmbTrace.trace("$name-init") { supplier() }
    }
}
