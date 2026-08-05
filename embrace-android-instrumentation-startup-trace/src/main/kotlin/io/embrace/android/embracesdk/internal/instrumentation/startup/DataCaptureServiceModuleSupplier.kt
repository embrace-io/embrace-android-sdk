package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

/**
 * Function that returns an instance of [DataCaptureServiceModule]. Matches the signature of the constructor for
 * [DataCaptureServiceModuleImpl]
 */
typealias DataCaptureServiceModuleSupplier = (
    clock: Clock,
    logger: InternalLogger,
    destination: TelemetryDestination,
    configService: ConfigService,
    startupClassifier: StartupClassifier,
    versionChecker: VersionChecker,
    activityManager: ActivityManager?,
    backgroundWorker: BackgroundWorker?,
) -> DataCaptureServiceModule
