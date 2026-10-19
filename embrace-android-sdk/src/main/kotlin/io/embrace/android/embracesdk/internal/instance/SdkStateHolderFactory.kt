package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.injection.ModuleGraphSupplier
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkDisabledException
import io.embrace.android.embracesdk.internal.utils.EmbTrace

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    preStartServices: PreStartServices = PreStartServices(),
    moduleGraphSupplier: ModuleGraphSupplier? = null,
    onModuleGraphCreated: ((InitializedModuleGraph) -> Unit)? = null,
): SdkStateHolder {
    val logger = preStartServices.logger
    val clock = preStartServices.clock
    val buffer = BufferingSdkInstance(
        clock = clock::now,
        logger = logger,
        internalErrorHandler = preStartServices.internalErrorHandler,
    )
    return SdkStateHolder(
        instanceFactory = { context ->
            EmbTrace.trace(sectionName = "modules-init", recordDuration = true) {
                try {
                    moduleGraphSupplier?.invoke(context, preStartServices, buffer)
                        ?: InitializedModuleGraph(context, preStartServices, buffer)
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
        preStart = buffer,
        failed = { NoopSdkInstance(logger, NoopSdkInstance.SDK_START_FAILED) },
        disabled = { NoopSdkInstance(logger, NoopSdkInstance.SDK_DISABLED) },
        telemetryService = preStartServices.telemetryService,
        errorHandler = logger,
        bufferedErrorHandler = preStartServices.internalErrorHandler,
    )
}
