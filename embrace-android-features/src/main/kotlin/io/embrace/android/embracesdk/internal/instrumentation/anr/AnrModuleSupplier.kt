package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

/**
 * Function that returns an instance of [AnrModule]. Matches the signature of the constructor for [AnrModuleImpl]
 */
typealias AnrModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    workerModule: WorkerThreadModule,
    processStateService: ProcessStateService
) -> AnrModule

fun createAnrModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    workerModule: WorkerThreadModule,
    processStateService: ProcessStateService
): AnrModule =
    AnrModuleImpl(initModule, openTelemetryModule, configService, workerModule, processStateService)
