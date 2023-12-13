package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService

/**
 * A module of components and services required at [EmbraceImpl] instantiation time, i.e. before the SDK evens starts
 */
internal interface InitModule {
    /**
     * Clock instance locked to the time of creation used by the SDK throughout its lifetime
     */
    val clock: Clock

    /**
     * Service to track usage of public APIs and other internal metrics
     */
    val embraceTelemetryService: EmbraceTelemetryService

    /**
     * Service to log traces
     */
    val spansService: SpansService
}

internal class InitModuleImpl(
    override val clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    override val embraceTelemetryService: EmbraceTelemetryService = EmbraceTelemetryService(),
    override val spansService: SpansService = EmbraceSpansService(OpenTelemetryClock(clock), embraceTelemetryService)
) : InitModule
