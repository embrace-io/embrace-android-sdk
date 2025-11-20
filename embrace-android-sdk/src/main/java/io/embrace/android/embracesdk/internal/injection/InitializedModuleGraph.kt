package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModuleSupplier
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Constructed module dependencies that will be used by the initialized SDK.
 */
@Suppress("UseCheckOrError")
internal class InitializedModuleGraph(
    context: Context,
    sdkStartTimeMs: Long,
    versionChecker: VersionChecker = BuildVersionChecker,
    private val initModule: InitModule,
    private val openTelemetryModule: OpenTelemetryModule,
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
    private val nativeCoreModuleSupplier: NativeCoreModuleSupplier,
    private val nativeFeatureModuleSupplier: NativeFeatureModuleSupplier,
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
            openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
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
            workerThreadModule,
            configModule,
            essentialServiceModule,
            coreModule,
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
            initModule,
            openTelemetryModule,
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
            essentialServiceModule.appStateTracker
        )
    }

    override val payloadSourceModule: PayloadSourceModule = init {
        payloadSourceModuleSupplier(
            initModule,
            coreModule,
            workerThreadModule,
            essentialServiceModule,
            configModule,
            { nativeCoreModule.symbolService.symbolsForCurrentArch },
            openTelemetryModule,
            { anrModule.anrOtelMapper },
            deliveryModule
        )
    }

    override val nativeCoreModule: NativeCoreModule = init {
        nativeCoreModuleSupplier(
            configModule,
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

    override val nativeFeatureModule: NativeFeatureModule = init {
        nativeFeatureModuleSupplier(
            nativeCoreModule,
            instrumentationModule,
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
            dataCaptureServiceModule.startupService,
            logModule
        )
    }

    private inline fun <reified T> init(supplier: () -> T): T {
        val module = T::class
        val name = module.simpleName?.removeSuffix("Module")?.lowercase() ?: "module"
        return EmbTrace.trace("$name-init") { supplier() }
    }
}
