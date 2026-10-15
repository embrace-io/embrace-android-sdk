package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.logging.BufferedInternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryServiceImpl

/**
 * Services that are absolutely required before the SDK has been initialized
 */
class BootstrapServices(
    val logger: InternalLogger = InternalLoggerImpl(),
    val clock: Clock = NormalizedIntervalClock(logger = logger),
    val systemInfo: SystemInfo = SystemInfo(),
    val telemetryService: InternalTelemetryService = InternalTelemetryServiceImpl(systemInfo = systemInfo),
    val internalErrorHandler: BufferedInternalErrorHandler = BufferedInternalErrorHandler(clock),
    val externalOtelConfig: ExternalOtelConfig = ExternalOtelConfig(),
) {

    init {
        logger.errorHandlerProvider = { internalErrorHandler }
    }
}
