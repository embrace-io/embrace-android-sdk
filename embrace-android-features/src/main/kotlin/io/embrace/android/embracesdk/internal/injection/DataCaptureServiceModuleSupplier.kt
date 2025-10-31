package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Function that returns an instance of [DataCaptureServiceModule]. Matches the signature of the constructor for
 * [DataCaptureServiceModuleImpl]
 */
typealias DataCaptureServiceModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    versionChecker: VersionChecker,
) -> DataCaptureServiceModule

fun createDataCaptureServiceModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    versionChecker: VersionChecker,
): DataCaptureServiceModule = DataCaptureServiceModuleImpl(
    initModule,
    openTelemetryModule,
    configService,
    versionChecker
)
