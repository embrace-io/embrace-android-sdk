@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    override val initModule: InitModule = EmbTrace.trace("init-module", code = ::InitModuleImpl),
    override val openTelemetryModule: OpenTelemetryModule = EmbTrace.trace("otel-module") {
        OpenTelemetryModuleImpl(initModule)
    },
    /*
     * The suppliers below exist purely as a seam for tests to inject fake modules. They default to
     * null rather than to a lambda that forwards to the real constructor as a default lambda allocates
     * a synthetic class per supplier.
     */
    private val coreModuleSupplier: CoreModuleSupplier? = null,
    private val configServiceSupplier: ConfigServiceSupplier? = null,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier? = null,
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
) : ModuleGraph {

    @Volatile
    private var delegate: ModuleGraph = UninitializedModuleGraph
    override val sdkStartTimeMs: Long get() = delegate.sdkStartTimeMs
    override val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker get() = delegate.sdkInitResourceUsageTracker
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
    override val userSessionOrchestrationModule: UserSessionOrchestrationModule get() = delegate.userSessionOrchestrationModule
    override val payloadSourceModule: PayloadSourceModule get() = delegate.payloadSourceModule

    /**
     * Returns true when the call has triggered an initialization, false if initialization is already in progress or is complete.
     */
    fun init(
        context: Context,
        versionChecker: VersionChecker = BuildVersionChecker,
    ): Boolean = EmbTrace.trace(sectionName = "modules-init", recordDuration = true) {
        try {
            if (isInitialized()) {
                return@trace false
            }
            // stamped before anything else so that the SDK init span covers all the work below,
            // and the perf samples cover the same interval as the span
            val startTimeMs = initModule.clock.now()
            val resourceUsageTracker = SdkInitResourceUsageTracker().apply { captureStart() }
            val keyValueStore = lazy { createKeyValueStore(context, initModule.jsonSerializer) }
            val persistedConfig = EmbTrace.trace(
                sectionName = "persisted-config-load",
                recordDuration = true,
            ) {
                PersistedConfig(
                    serializer = initModule.jsonSerializer,
                    filesDir = context.filesDir,
                    instrumentedConfig = initModule.instrumentedConfig,
                    keyValueStore = keyValueStore,
                    uuidSource = initModule.uuidSource,
                )
            }
            openTelemetryModule.setOtelBehavior(persistedConfig.otelBehavior)

            val workerThreadModule = EmbTrace.trace("workerthread-init") {
                workerThreadModuleSupplier?.invoke() ?: WorkerThreadModuleImpl()
            }
            prewarmSharedPreferences(context, workerThreadModule)
            delegate = InitializedModuleGraph(
                context,
                versionChecker,
                startTimeMs,
                resourceUsageTracker,
                initModule,
                openTelemetryModule,
                workerThreadModule,
                keyValueStore,
                persistedConfig,
                coreModuleSupplier,
                configServiceSupplier,
                storageServiceSupplier,
                essentialServiceModuleSupplier,
                featureModuleSupplier,
                instrumentationModuleSupplier,
                dataCaptureServiceModuleSupplier,
                deliveryModuleSupplier,
                threadBlockageServiceSupplier,
                logModuleSupplier,
                userSessionOrchestrationModuleSupplier,
                payloadSourceModuleSupplier,
            )
            isInitialized()
        } catch (ignored: SdkDisabledException) {
            // do nothing - avoid instantiating SDK code any more than necessary.
            false
        }
    }

    fun stop() {
        if (!isInitialized()) {
            return
        }
        essentialServiceModule.networkConnectivityService.close()
        openTelemetryModule.otelSdkConfig.shutdownExport()
        workerThreadModule.close()
        delegate = UninitializedModuleGraph
    }

    private fun isInitialized(): Boolean = delegate != UninitializedModuleGraph

    /**
     * Touches the default `SharedPreferences` on a background worker as early as possible to speculatively
     * attempt to reduce the amount of time Android blocks with `awaitLoadedLocked` later on in SDK init.
     */
    private fun prewarmSharedPreferences(context: Context, workerThreadModule: WorkerThreadModule) {
        workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker).submit {
            try {
                PreferenceManager.getDefaultSharedPreferences(context)
            } catch (ignored: Throwable) {
            }
        }
    }
}
