package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorService

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
