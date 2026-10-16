package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.api.delegate.PreStartOTelApi
import io.embrace.android.embracesdk.internal.injection.BootstrapServices
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleGraph
import io.embrace.android.embracesdk.internal.injection.ModuleGraphFactory
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    bootstrapServices: BootstrapServices = BootstrapServices(),
    moduleGraphFactoryProvider: Provider<ModuleGraphFactory> = {
        EmbTrace.trace(sectionName = "bootstrapper-init", recordDuration = true) {
            val initModule = EmbTrace.trace("init-module") {
                InitModuleImpl(bootstrapServices)
            }
            ModuleGraphFactory(
                initModule = initModule,
                openTelemetryModule = EmbTrace.trace("otel-module") {
                    OpenTelemetryModuleImpl(initModule, bootstrapServices.externalOtelConfig)
                },
            )
        }
    },
    onModuleGraphCreated: ((ModuleGraph) -> Unit)? = null,
): SdkStateHolder {
    val logger = bootstrapServices.logger
    val clock = bootstrapServices.clock
    val moduleGraphFactory = moduleGraphFactoryProvider()
    val buffer = BufferingSdkInstance(
        clock = clock::now,
        logger = logger,
        errorHandler = bootstrapServices.errorHandler,
    )
    return SdkStateHolder(
        instanceFactory = { context ->
            // drain before the module graph builds the OTel SDK
            buffer.drainOTelConfig(PreStartOTelApi(moduleGraphFactory.openTelemetryModule.otelSdkConfig.externalConfig))
            moduleGraphFactory.init(context)?.let { moduleGraph ->
                onModuleGraphCreated?.invoke(moduleGraph)
                RealSdkInstance(
                    moduleGraph = moduleGraph,
                    clock = clock,
                )
            }
        },
        preStart = { buffer },
        failed = { NoopSdkInstance(logger, NoopSdkInstance.SDK_START_FAILED) },
        disabled = { NoopSdkInstance(logger, NoopSdkInstance.SDK_DISABLED) },
        telemetryService = bootstrapServices.telemetryService,
        errorHandler = logger,
    )
}
