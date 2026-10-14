package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
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
    bootstrapperProvider: Provider<ModuleInitBootstrapper> = {
        EmbTrace.trace(sectionName = "bootstrapper-init", recordDuration = true) {
            val initModule = EmbTrace.trace("init-module") {
                InitModuleImpl(
                    logger = logger,
                    clock = clock,
                    telemetryService = telemetryService,
                    internalErrorHandler = internalErrorHandler,
                )
            }
            ModuleInitBootstrapper(
                initModule = initModule,
                openTelemetryModule = EmbTrace.trace("otel-module") { OpenTelemetryModuleImpl(initModule) },
            )
        }
    },
): SdkStateHolder {
    var holder: SdkStateHolder? = null
    holder = SdkStateHolder(
        instance = {
            RealSdkInstance(
                bootstrapper = bootstrapperProvider(),
                hostedSdkApiProvider = { checkNotNull(holder?.api) },
                clock = clock,
            )
        },
        preStart = {
            BufferingSdkInstance(
                clock = clock::now,
                logger = logger,
            )
        },
        disabled = { NoopSdkInstance(logger, NoopSdkInstance.SDK_DISABLED) },
        telemetryService = telemetryService,
        errorHandler = logger,
    )
    return holder
}
