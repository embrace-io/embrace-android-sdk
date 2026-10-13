package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.api.delegate.LateBindingOpenTelemetry
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.logging.BufferedInternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryServiceImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.opentelemetry.kotlin.NoopOpenTelemetry

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
            ModuleInitBootstrapper(
                initModule = EmbTrace.trace("init-module") {
                    InitModuleImpl(
                        logger = logger,
                        clock = clock,
                        telemetryService = telemetryService,
                        internalErrorHandler = internalErrorHandler,
                    )
                },
            )
        }
    },
): SdkStateHolder {
    val bootstrapper = lazy(bootstrapperProvider)
    val sdkCallChecker = SdkCallChecker(logger)
    val openTelemetryKotlin = LateBindingOpenTelemetry {
        if (sdkCallChecker.started.get()) {
            bootstrapper.value.openTelemetryModule.otelSdkWrapper.openTelemetryKotlin
        } else {
            NoopOpenTelemetry
        }
    }
    lateinit var holder: SdkStateHolder
    val impl = EmbraceImpl(bootstrapper, { holder.api }, sdkCallChecker, openTelemetryKotlin, clock, logger)
    holder = SdkStateHolder(impl, telemetryService, logger, impl)
    return holder
}
