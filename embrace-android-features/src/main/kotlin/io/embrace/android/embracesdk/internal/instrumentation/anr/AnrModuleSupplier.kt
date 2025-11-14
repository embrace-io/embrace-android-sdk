package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule

/**
 * Function that returns an instance of [AnrModule]. Matches the signature of the constructor for [AnrModuleImpl]
 */
typealias AnrModuleSupplier = (
    instrumentationModule: InstrumentationModule,
    openTelemetryModule: OpenTelemetryModule,
    appStateTracker: AppStateTracker,
) -> AnrModule

fun createAnrModule(
    instrumentationModule: InstrumentationModule,
    openTelemetryModule: OpenTelemetryModule,
    appStateTracker: AppStateTracker,
): AnrModule =
    AnrModuleImpl(instrumentationModule, openTelemetryModule, appStateTracker)
