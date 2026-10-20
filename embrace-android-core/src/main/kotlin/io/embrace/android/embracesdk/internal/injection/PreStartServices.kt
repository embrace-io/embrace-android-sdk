package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryServiceImpl

/**
 * Services created when the Embrace object is first loaded, before start() is called
 */
class PreStartServices(
    val errorHandler: ErrorHandler = ErrorHandlerImpl(),
    val clock: Clock = NormalizedIntervalClock(errorHandler = errorHandler),
    val systemInfo: SystemInfo = SystemInfo(),
    val telemetryService: InternalTelemetryService = InternalTelemetryServiceImpl(systemInfo = systemInfo),
    val externalOtelConfig: ExternalOtelConfig = ExternalOtelConfig(),
)
