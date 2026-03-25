package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.payload.Envelope
import java.util.concurrent.Future

/**
 * IntakeService is responsible for storing telemetry payloads to disk and notifying the
 * scheduling service whenever a payload is ready for sending. It has the following responsibilities:
 *
 * 1. Cache arbitrary payload types to disk (encoding priority & other metadata in the filename)
 * 2. Clean up any telemetry that exceeds our max disk usage policy
 * 3. Notify the scheduling service after a payload is stored to disk & is ready to send
 * 4. Handle process termination gracefully by waiting until all payloads in the queue have been written to disk
 * 5. If required, clean up any cached data that is no longer needed after the intake is successful
 */
interface IntakeService : Shutdownable {

    /**
     * Stores the payload [intake] on disk as its JSON representation and associate it in the storage layer with [metadata].
     *
     * If [staleEntry] is non-null, the payload associated with it will be deleted once the new payload is successfully stored.
     */
    fun take(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata? = null,
    ): Future<*>
}
