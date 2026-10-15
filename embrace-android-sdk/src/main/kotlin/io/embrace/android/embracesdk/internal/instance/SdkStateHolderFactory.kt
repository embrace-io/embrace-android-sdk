package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.api.delegate.PreStartOTelApi
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleGraph
import io.embrace.android.embracesdk.internal.injection.ModuleGraphFactory
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.logging.BufferedInternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryServiceImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Assembles the [SdkStateHolder] that backs the public `Embrace` object. Production code and integration tests both
 * build the SDK through here so that its wiring only needs to change in one place.
 */
internal fun createSdkStateHolder(
    logger: InternalLogger = InternalLoggerImpl(),
    clock: Clock = NormalizedIntervalClock(logger = logger),
    telemetryService: InternalTelemetryService = InternalTelemetryServiceImpl(systemInfo = SystemInfo()),
    internalErrorHandler: BufferedInternalErrorHandler = BufferedInternalErrorHandler(clock).also {
        logger.errorHandlerProvider = { it }
    },
    moduleGraphFactoryProvider: Provider<ModuleGraphFactory> = {
        EmbTrace.trace(sectionName = "bootstrapper-init", recordDuration = true) {
            val initModule = EmbTrace.trace("init-module") {
                InitModuleImpl(
                    logger = logger,
                    clock = clock,
                    telemetryService = telemetryService,
                    internalErrorHandler = internalErrorHandler,
                )
            }
            ModuleGraphFactory(
                initModule = initModule,
                openTelemetryModule = EmbTrace.trace("otel-module") { OpenTelemetryModuleImpl(initModule) },
            )
        }
    },
    onModuleGraphCreated: ((ModuleGraph) -> Unit)? = null,
): SdkStateHolder {
    val moduleGraphFactory = moduleGraphFactoryProvider()
    val buffer = BufferingSdkInstance(
        clock = clock::now,
        logger = logger,
    )
    return SdkStateHolder(
        instanceFactory = { context ->
            // drain before the module graph builds the OTel SDK
            buffer.drainOTelConfig(PreStartOTelApi(moduleGraphFactory.openTelemetryModule.otelSdkConfig))
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
        telemetryService = telemetryService,
        errorHandler = logger,
    )
}
