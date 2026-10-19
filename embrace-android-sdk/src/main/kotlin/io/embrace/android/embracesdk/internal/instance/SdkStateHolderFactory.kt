package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import io.embrace.android.embracesdk.internal.api.delegate.PreStartOTelApi
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServicesSupplier
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.injection.ModuleGraphSupplier
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.utils.AndroidSectionRecorder
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.SystemTrace

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    preStartServices: PreStartServices = PreStartServices(),
    configLoadServicesSupplier: ConfigLoadServicesSupplier? = null,
    moduleGraphSupplier: ModuleGraphSupplier? = null,
    sdkInitResourceUsageTrackerSupplier: Provider<SdkInitResourceUsageTracker>? = null,
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
                createModuleGraph(
                    context,
                    preStartServices,
                    buffer,
                    configLoadServicesSupplier,
                    moduleGraphSupplier,
                    sdkInitResourceUsageTrackerSupplier,
                )
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

/**
 * Returns the [InitializedModuleGraph], or null if the SDK is disabled. A disabled SDK never
 * constructs the module graph to reduce SDK init cost.
 */
private fun createModuleGraph(
    context: Context,
    preStartServices: PreStartServices,
    buffer: BufferingSdkInstance,
    configLoadServicesSupplier: ConfigLoadServicesSupplier?,
    moduleGraphSupplier: ModuleGraphSupplier?,
    sdkInitResourceUsageTrackerSupplier: Provider<SdkInitResourceUsageTracker>?,
): InitializedModuleGraph? {
    SystemTrace.recorder = AndroidSectionRecorder

    // stamped before anything else so that the SDK init span covers all the work below,
    // and the perf samples cover the same interval as the span
    val sdkStartTimeMs = preStartServices.clock.now()

    // drained before the config service is created, as it checks whether OTLP export has been configured
    buffer.drainOTelConfig(PreStartOTelApi(preStartServices.externalOtelConfig))
    val sdkInitResourceUsageTracker =
        (sdkInitResourceUsageTrackerSupplier?.invoke() ?: SdkInitResourceUsageTracker(preStartServices.logger)).apply {
            captureStart()
        }

    val configLoadServices = configLoadServicesSupplier?.invoke(context, preStartServices)
        ?: ConfigLoadServices(context, preStartServices)

    val moduleGraph = try {
        configLoadServices.start()
        val sdkDisabled = EmbTrace.trace("sdk-disable-check") {
            configLoadServices.configService.config.sdkMode.sdkDisabled
        }
        if (sdkDisabled) {
            null
        } else {
            moduleGraphSupplier?.invoke(
                context,
                preStartServices,
                configLoadServices,
                sdkStartTimeMs,
                sdkInitResourceUsageTracker,
            ) ?: InitializedModuleGraph(
                context,
                preStartServices,
                configLoadServices,
                sdkStartTimeMs,
                sdkInitResourceUsageTracker,
            )
        }
    } catch (exc: Throwable) {
        configLoadServices.close()
        throw exc
    }

    // the module graph owns the config load services' resources once constructed
    if (moduleGraph == null) {
        configLoadServices.close()
    }
    return moduleGraph
}
