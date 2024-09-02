package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService

/**
 * A module of components and services required at [EmbraceImpl] instantiation time, i.e. before the SDK evens starts
 */
public interface InitModule {
    /**
     * Clock instance locked to the time of creation used by the SDK throughout its lifetime
     */
    public val clock: io.embrace.android.embracesdk.internal.clock.Clock

    /**
     * Service to track usage of public APIs and other internal metrics
     */
    public val telemetryService: TelemetryService

    /**
     * Logger used by the SDK
     */
    public val logger: EmbLogger

    /**
     * Info about the system available at startup time without expensive disk or API calls
     */
    public val systemInfo: SystemInfo

    /**
     * Tracks internal errors
     */
    public val internalErrorService: InternalErrorService

    /**
     * Returns the serializer used to serialize data to JSON
     */
    public val jsonSerializer: PlatformSerializer
}
