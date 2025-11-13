package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateService

/**
 * Function that returns an instance of [AnrModule]. Matches the signature of the constructor for [AnrModuleImpl]
 */
typealias AnrModuleSupplier = (
    instrumentationModule: InstrumentationModule,
    openTelemetryModule: OpenTelemetryModule,
    appStateService: AppStateService,
) -> AnrModule

fun createAnrModule(
    instrumentationModule: InstrumentationModule,
    openTelemetryModule: OpenTelemetryModule,
    appStateService: AppStateService,
): AnrModule =
    AnrModuleImpl(instrumentationModule, openTelemetryModule, appStateService)
