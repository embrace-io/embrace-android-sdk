package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.internal.errors.EmbraceInternalErrorService
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorService
import io.embrace.android.embracesdk.internal.IdGenerator
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService

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
    val logger: EmbLogger

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

    /**
     * Tracks internal errors
     */
    val internalErrorService: InternalErrorService

    /**
     * Returns the serializer used to serialize data to JSON
     */
    val jsonSerializer: EmbraceSerializer
}

internal class InitModuleImpl(
    override val clock: io.embrace.android.embracesdk.internal.clock.Clock =
        NormalizedIntervalClock(systemClock = SystemClock()),
    override val openTelemetryClock: io.opentelemetry.sdk.common.Clock = OpenTelemetryClock(embraceClock = clock),
    override val logger: EmbLogger = EmbLoggerImpl(),
    override val systemInfo: SystemInfo = SystemInfo()
) : InitModule {

    override val internalErrorService: InternalErrorService = EmbraceInternalErrorService()

    init {
        logger.internalErrorService = internalErrorService
    }

    override val telemetryService: TelemetryService by singleton {
        EmbraceTelemetryService(
            systemInfo = systemInfo
        )
    }

    override val processIdentifier: String = IdGenerator.generateLaunchInstanceId()

    override val jsonSerializer: EmbraceSerializer by singleton {
        EmbraceSerializer()
    }
}
