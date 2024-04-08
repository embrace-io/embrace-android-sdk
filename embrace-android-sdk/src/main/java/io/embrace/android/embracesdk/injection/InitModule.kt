package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.IdGenerator
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.SystemInfo
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

    /**
     * Info about the system available at startup time without expensive disk or API calls
     */
    val systemInfo: SystemInfo

    /**
     * Unique ID generated for an instance of the app process and not related to the actual process ID assigned by the OS.
     * This allows us to explicitly relate all the sessions associated with a particular app launch rather than having the backend figure
     * this out by proximity for stitched sessions.
     */
    val processIdentifier: String
}

internal class InitModuleImpl(
    override val clock: io.embrace.android.embracesdk.internal.clock.Clock =
        NormalizedIntervalClock(systemClock = SystemClock()),
    override val openTelemetryClock: io.opentelemetry.sdk.common.Clock = OpenTelemetryClock(embraceClock = clock),
    override val logger: InternalEmbraceLogger = InternalEmbraceLogger(),
    override val systemInfo: SystemInfo = SystemInfo()
) : InitModule {

    override val telemetryService: TelemetryService by singleton {
        EmbraceTelemetryService(
            systemInfo = systemInfo
        )
    }

    override val processIdentifier: String = IdGenerator.generateLaunchInstanceId()
}
