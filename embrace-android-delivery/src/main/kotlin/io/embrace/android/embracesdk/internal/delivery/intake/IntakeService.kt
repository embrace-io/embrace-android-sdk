package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.payload.Envelope

/**
 * IntakeService is responsible for storing telemetry payloads to disk and notifying the
 * scheduling service whenever a payload is ready for sending. It has the following responsibilities:
 *
 * 1. Cache arbitrary payload types to disk (encoding priority & other metadata in the filename)
 * 2. Clean up any telemetry that exceeds our max disk usage policy
 * 3. Notify the scheduling service after a payload is stored to disk & is ready to send
 * 4. Handle process termination gracefully by waiting until all payloads in the queue have been
 * written to disk
 */
interface IntakeService : Shutdownable {

    /**
     * Stores a payload on disk as its JSON representation.
     */
    fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata)
}
