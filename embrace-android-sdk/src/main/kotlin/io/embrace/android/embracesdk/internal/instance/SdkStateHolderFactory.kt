package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import io.embrace.android.embracesdk.internal.api.delegate.PreStartOTelApi
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServicesSupplier
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
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
    sdkInitResourceUsageTrackerSupplier: Provider<SdkInitResourceUsageTracker>? = null,
    overrides: SdkOverrides? = null,
): SdkStateHolder {
    val errorHandler = preStartServices.errorHandler
    val clock = preStartServices.clock
    val buffer = EmbTrace.trace("buffering-sdk-instance-init") {
        BufferingSdkInstance(
            clock = clock::now,
            errorHandler = errorHandler,
        )
    }
    return EmbTrace.trace("sdk-state-holder-init") {
        SdkStateHolder(
            instanceFactory = { context ->
                EmbTrace.trace(sectionName = "modules-init", recordDuration = true) {
                    createModuleGraph(
                        context,
                        preStartServices,
                        buffer,
                        configLoadServicesSupplier,
                        sdkInitResourceUsageTrackerSupplier,
                        overrides,
                    )
                }?.let { moduleGraph ->
                    EmbTrace.trace("real-sdk-instance-init") {
                        createRealSdkInstance(
                            moduleGraph = moduleGraph,
                            clock = clock,
                        )
                    }
                }
            },
            preStart = buffer,
            failed = { NoopSdkInstance(errorHandler, NoopSdkInstance.SDK_START_FAILED) },
            disabled = { NoopSdkInstance(errorHandler, NoopSdkInstance.SDK_DISABLED) },
            telemetryService = preStartServices.telemetryService,
            errorHandler = errorHandler,
        )
    }
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
    sdkInitResourceUsageTrackerSupplier: Provider<SdkInitResourceUsageTracker>?,
    overrides: SdkOverrides?,
): InitializedModuleGraph? {
    SystemTrace.recorder = AndroidSectionRecorder

    // stamped before anything else so that the SDK init span covers all the work below,
    // and the perf samples cover the same interval as the span
    val sdkStartTimeMs = preStartServices.clock.now()

    // drained before the config service is created, as it checks whether OTLP export has been configured
    EmbTrace.trace("drain-otel-config") {
        buffer.drainOTelConfig(PreStartOTelApi(preStartServices.externalOtelConfig))
    }
    val sdkInitResourceUsageTracker = EmbTrace.trace("sdk-init-resource-usage-tracker-init") {
        (sdkInitResourceUsageTrackerSupplier?.invoke() ?: SdkInitResourceUsageTracker(preStartServices.errorHandler)).apply {
            captureStart()
        }
    }

    val configLoadServices = EmbTrace.trace("config-load-services-init") {
        configLoadServicesSupplier?.invoke(context, preStartServices)
            ?: ConfigLoadServices(context, preStartServices)
    }

    val moduleGraph = try {
        EmbTrace.trace("config-load-services-start") {
            configLoadServices.start()
        }
        val sdkDisabled = EmbTrace.trace("sdk-disable-check") {
            configLoadServices.configService.config.sdkMode.sdkDisabled
        }
        if (sdkDisabled) {
            null
        } else {
            val graph = EmbTrace.trace("initialized-module-graph-init") {
                InitializedModuleGraph(
                    context,
                    preStartServices,
                    configLoadServices,
                    sdkStartTimeMs,
                    sdkInitResourceUsageTracker,
                    overrides = overrides,
                )
            }
            EmbTrace.trace("initialized-module-graph-start") {
                graph.start()
            }
            graph
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
