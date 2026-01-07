package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Function that returns an instance of [DataCaptureServiceModule]. Matches the signature of the constructor for
 * [DataCaptureServiceModuleImpl]
 */
typealias DataCaptureServiceModuleSupplier = (
    clock: Clock,
    logger: InternalLogger,
    destination: TelemetryDestination,
    configService: ConfigService,
    versionChecker: VersionChecker,
) -> DataCaptureServiceModule
