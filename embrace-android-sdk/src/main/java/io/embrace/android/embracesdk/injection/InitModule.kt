package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.telemetry.TelemetryService

/**
 * A module of components and services required at [EmbraceImpl] instantiation time, i.e. before the SDK evens starts
 */
internal interface InitModule {
    /**
     * Clock instance locked to the time of creation used by the SDK throughout its lifetime
     */
    val clock: io.embrace.android.embracesdk.internal.clock.Clock

    /**
     * OpenTelemetry SDK compatible clock based on [clock]
     */
    val openTelemetryClock: io.opentelemetry.sdk.common.Clock

    /**
     * Service to track usage of public APIs and other internal metrics
     */
    val telemetryService: TelemetryService

    /**
     * Logger used by the SDK
     */
    val logger: InternalEmbraceLogger
}

internal class InitModuleImpl(
    override val clock: io.embrace.android.embracesdk.internal.clock.Clock =
        NormalizedIntervalClock(systemClock = SystemClock()),
    override val openTelemetryClock: io.opentelemetry.sdk.common.Clock = OpenTelemetryClock(embraceClock = clock),
    override val logger: InternalEmbraceLogger = InternalEmbraceLogger()
) : InitModule {

    override val telemetryService: TelemetryService by singleton {
        EmbraceTelemetryService()
    }
}
