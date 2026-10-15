package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.injection.BootstrapServices
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.injection.ModuleGraphSupplier
import io.embrace.android.embracesdk.internal.injection.SdkDisabledException
import io.embrace.android.embracesdk.internal.utils.EmbTrace

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    bootstrapServices: BootstrapServices = BootstrapServices(),
    moduleGraphSupplier: ModuleGraphSupplier? = null,
    onModuleGraphCreated: ((InitializedModuleGraph) -> Unit)? = null,
): SdkStateHolder {
    val logger = bootstrapServices.logger
    val clock = bootstrapServices.clock
    val buffer = BufferingSdkInstance(
        clock = clock::now,
        logger = logger,
        internalErrorHandler = bootstrapServices.internalErrorHandler,
    )
    return SdkStateHolder(
        instanceFactory = { context ->
            EmbTrace.trace(sectionName = "modules-init", recordDuration = true) {
                try {
                    moduleGraphSupplier?.invoke(context, bootstrapServices, buffer)
                        ?: InitializedModuleGraph(context, bootstrapServices, buffer)
                } catch (ignored: SdkDisabledException) {
                    // do nothing - avoid instantiating SDK code any more than necessary.
                    null
                }
            }?.let { moduleGraph ->
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
