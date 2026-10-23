package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.delegate.PreStartOTelApi
import io.embrace.android.embracesdk.internal.delivery.storage.deleteSdkFiles
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServicesSupplier
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.utils.AndroidSectionRecorder
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.util.concurrent.Executors

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
    // the context the SDK is started with, recorded before its instance is created, so that the instances standing in
    // for one a start didn't leave can find the SDK's files to delete
    var startContext: Context? = null
    return EmbTrace.trace("sdk-state-holder-init") {
        SdkStateHolder(
            realSdkInstanceFactory = { context ->
                startContext = context
                EmbTrace.trace(sectionName = "modules-init", recordDuration = true) {
                    createSdkInstance(
                        context,
                        preStartServices,
                        buffer,
                        configLoadServicesSupplier,
                        sdkInitResourceUsageTrackerSupplier,
                        overrides,
                    )
                }
            },
            preStart = buffer,
            failed = lazy {
                NoopSdkInstance(
                    clock = clock::now,
                    errorHandler = errorHandler,
                    message = NoopSdkInstance.SDK_START_FAILED,
                    deleteData = startContext?.let(::sdkFileDeletion),
                )
            },
            disabled = lazy {
                NoopSdkInstance(
                    clock = clock::now,
                    errorHandler = errorHandler,
                    message = NoopSdkInstance.SDK_DISABLED,
                    deleteData = startContext?.let(::sdkFileDeletion),
                )
            },
            telemetryService = preStartServices.telemetryService,
            errorHandler = errorHandler,
        )
    }
}

/**
 * Returns the [RealSdkInstance], or null if the SDK is disabled. A disabled SDK never
 * constructs its services to reduce SDK init cost.
 */
private fun createSdkInstance(
    context: Context,
    preStartServices: PreStartServices,
    buffer: BufferingSdkInstance,
    configLoadServicesSupplier: ConfigLoadServicesSupplier?,
    sdkInitResourceUsageTrackerSupplier: Provider<SdkInitResourceUsageTracker>?,
    overrides: SdkOverrides?,
): RealSdkInstance? {
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

    val instance = try {
        EmbTrace.trace("config-load-services-start") {
            configLoadServices.start()
        }
        val sdkDisabled = EmbTrace.trace("sdk-disable-check") {
            configLoadServices.configService.config.sdkMode.sdkDisabled
        }
        if (sdkDisabled) {
            null
        } else {
            createRealSdkInstance(
                context,
                preStartServices,
                configLoadServices,
                sdkStartTimeMs,
                sdkInitResourceUsageTracker,
                overrides,
            )
        }
    } catch (exc: Throwable) {
        configLoadServices.close()
        throw exc
    }

    // the SDK instance owns the config load services' resources once constructed
    if (instance == null) {
        configLoadServices.close()
    }
    return instance
}

/**
 * Returns what deletes the SDK's files for an instance that stands in for one a start didn't leave, or null if the
 * [context] can't say where they are. Each deletion, which is rare, runs on a thread of its own, as no worker exists then.
 */
private fun sdkFileDeletion(context: Context): (() -> Unit)? {
    val rootDir = runCatching { context.filesDir }.getOrNull() ?: return null
    val fallbackDir = runCatching { context.cacheDir }.getOrNull() ?: return null
    return {
        Executors.newSingleThreadExecutor().apply {
            execute {
                runCatching {
                    deleteSdkFiles(rootDir, fallbackDir)
                }.onFailure { exception ->
                    Log.e("[Embrace]", "An error occurred while trying to disable Embrace SDK.", exception)
                }
            }
            shutdown()
        }
    }
}
