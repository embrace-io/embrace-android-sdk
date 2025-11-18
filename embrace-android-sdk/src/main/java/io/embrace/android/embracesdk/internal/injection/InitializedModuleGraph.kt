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
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import kotlin.reflect.KClass

/**
 * Constructed module dependencies that will be used by the initialized SDK.
 */
@Suppress("UseCheckOrError")
internal class InitializedModuleGraph(
    context: Context,
    sdkStartTimeMs: Long,
    versionChecker: VersionChecker = BuildVersionChecker,
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
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

    override val coreModule: CoreModule = init(
        module = CoreModule::class,
        initAction = { coreModuleSupplier(context, initModule) }
    )

    override val workerThreadModule: WorkerThreadModule = init(
        module = WorkerThreadModule::class,
        initAction = {
            workerThreadModuleSupplier()
        },
        postAction = {
            EmbTrace.trace("span-service-init") {
                openTelemetryModule.spanService.initializeService(sdkStartTimeMs)
            }
        }
    )

    override val configModule: ConfigModule = init(
        module = ConfigModule::class,
        initAction = {
            configModuleSupplier(
                initModule,
                coreModule,
                openTelemetryModule,
                workerThreadModule,
            )
        },
        postAction = { module ->
            openTelemetryModule.applyConfiguration(
                sensitiveKeysBehavior = module.configService.sensitiveKeysBehavior,
                bypassValidation = module.configService.isOnlyUsingOtelExporters(),
                otelBehavior = module.configService.otelBehavior
            )

            EmbTrace.trace("sdk-disable-check") {
                // kick off config HTTP request first so the SDK can't get in a permanently disabled state
                EmbTrace.trace("load-config-response") {
                    module.combinedRemoteConfigSource?.scheduleConfigRequests()
                }

                EmbTrace.trace("behavior-check") {
                    if (module.configService.sdkModeBehavior.isSdkDisabled()) {
                        // bail out early. Caught at a higher-level that relies on this specific type
                        throw SdkDisabledException()
                    }
                }
            }
        }
    )

    override val storageModule: StorageModule = init(
        module = StorageModule::class,
        initAction = {
            storageModuleSupplier(initModule, coreModule, workerThreadModule)
        }
    )

    override val essentialServiceModule: EssentialServiceModule = init(
        module = EssentialServiceModule::class,
        initAction = {
            essentialServiceModuleSupplier(
                initModule,
                configModule,
                openTelemetryModule,
                coreModule,
                workerThreadModule,
                { null },
                { null },
            )
        },
        postAction = { module ->
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                EmbTrace.trace("network-connectivity-registration") {
                    module.networkConnectivityService.register()
                }
            }
        }
    )

    override val instrumentationModule: InstrumentationModule = init(
        module = InstrumentationModule::class,
        initAction = {
            instrumentationModuleSupplier(
                initModule,
                workerThreadModule,
                configModule,
                essentialServiceModule,
                coreModule,
            )
        }
    )

    override val featureModule: FeatureModule = init(
        module = FeatureModule::class,
        initAction = {
            featureModuleSupplier(
                instrumentationModule,
                configModule.configService,
                storageModule,
            )
        },
        postAction = { module ->
            initModule.logger.errorHandlerProvider = { module.internalErrorDataSource.dataSource }
        }
    )

    override val dataCaptureServiceModule: DataCaptureServiceModule = init(
        module = DataCaptureServiceModule::class,
        initAction = {
            dataCaptureServiceModuleSupplier(
                initModule,
                openTelemetryModule,
                configModule.configService,
                versionChecker,
            )
        },
        postAction = { module ->
            EmbTrace.trace("startup-tracker") {
                coreModule.application.registerActivityLifecycleCallbacks(
                    module.startupTracker
                )
            }
        }
    )

    override val deliveryModule: DeliveryModule = init(
        module = DeliveryModule::class,
        initAction = {
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
        },
        postAction = { module ->
            module.payloadCachingService?.run {
                openTelemetryModule.spanRepository.setSpanUpdateNotifier {
                    reportBackgroundActivityStateChange()
                }
            }
        }
    )

    override val anrModule: AnrModule = init(
        module = AnrModule::class,
        initAction = {
            anrModuleSupplier(
                instrumentationModule.instrumentationArgs,
                essentialServiceModule.appStateTracker
            )
        },
        postAction = { module ->
            module.anrService?.startAnrCapture()
        }
    )

    override val payloadSourceModule: PayloadSourceModule = init(
        module = PayloadSourceModule::class,
        initAction = {
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
        },
        postAction = { module ->
            module.metadataService.precomputeValues()
        }
    )

    override val nativeCoreModule: NativeCoreModule = init(
        module = NativeCoreModule::class,
        initAction = {
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
    )

    override val nativeFeatureModule: NativeFeatureModule = init(
        module = NativeFeatureModule::class,
        initAction = {
            nativeFeatureModuleSupplier(
                nativeCoreModule,
                instrumentationModule,
            )
        },
        postAction = { module ->
            nativeCoreModule.sharedObjectLoader.loadEmbraceNative()
            nativeCoreModule.nativeCrashHandlerInstaller?.install()
        }
    )

    override val logModule: LogModule = init(
        module = LogModule::class,
        initAction = {
            logModuleSupplier(
                initModule,
                openTelemetryModule,
                essentialServiceModule,
                configModule,
                deliveryModule,
                workerThreadModule,
                payloadSourceModule,
            )
        },
        postAction = { module ->
            // Start the log orchestrator
            openTelemetryModule.logSink.registerLogStoredCallback {
                module.logOrchestrator.onLogsAdded()
            }
        }
    )

    override val sessionOrchestrationModule: SessionOrchestrationModule = init(
        module = SessionOrchestrationModule::class,
        initAction = {
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
        },
        postAction = { module ->
            essentialServiceModule.telemetryDestination.sessionUpdateAction =
                module.sessionOrchestrator::onSessionDataUpdate
        }
    )

    private fun <T> init(
        module: KClass<*>,
        initAction: Provider<T>,
        postAction: (module: T) -> Unit = {},
    ): T {
        val name = module.simpleName?.removeSuffix("Module")?.lowercase() ?: "module"
        return EmbTrace.trace("$name-init") {
            initAction().apply(postAction)
        }
    }
}
