package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
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
    private val storageModuleSupplier: StorageModuleSupplier,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier,
    private val featureModuleSupplier: FeatureModuleSupplier,
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier,
    private val deliveryModuleSupplier: DeliveryModuleSupplier,
    private val anrModuleSupplier: AnrModuleSupplier,
    private val logModuleSupplier: LogModuleSupplier,
    private val sessionOrchestrationModuleSupplier: SessionOrchestrationModuleSupplier,
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

    override val storageModule: StorageModule = init {
        storageModuleSupplier(initModule, coreModule, workerThreadModule)
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
            storageModule,
        )
    }

    override val featureModule: FeatureModule = init {
        featureModuleSupplier(
            instrumentationModule,
            configModule.configService,
            storageModule,
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

    override val anrModule: AnrModule = init {
        anrModuleSupplier(
            instrumentationModule.instrumentationArgs,
        )
    }

    override val payloadSourceModule: PayloadSourceModule = init {
        payloadSourceModuleSupplier(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configModule,
            openTelemetryModule,
            { anrModule.anrOtelMapper },
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

    override val sessionOrchestrationModule: SessionOrchestrationModule = init {
        sessionOrchestrationModuleSupplier(
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
