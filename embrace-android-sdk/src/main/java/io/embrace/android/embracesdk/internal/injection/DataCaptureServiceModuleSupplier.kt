package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Function that returns an instance of [DataCaptureServiceModule]. Matches the signature of the constructor for
 * [DataCaptureServiceModuleImpl]
 */
internal typealias DataCaptureServiceModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule,
    versionChecker: VersionChecker,
    dataSourceModule: DataSourceModule
) -> DataCaptureServiceModule

internal fun createDataCaptureServiceModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule,
    versionChecker: VersionChecker,
    dataSourceModule: DataSourceModule
): DataCaptureServiceModule = DataCaptureServiceModuleImpl(
    initModule,
    openTelemetryModule,
    configService,
    workerThreadModule,
    versionChecker,
    dataSourceModule
)
