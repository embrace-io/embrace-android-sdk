package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.payload.Envelope

/**
 * This service caches in-memory data in case the process terminates. If the process terminates
 * the [PayloadResurrectionService] will convert the cached file into a full payload on next launch
 * that is then processed by the [IntakeService].
 */
interface PayloadCachingService : Shutdownable {

    /**
     * Starts caching an envelope on disk & returns a unique identifier for the caching attempt.
     *
     * [envelopeProvider] can be invoked multiple times whenever the service deems it necessary to
     * start caching.
     * [metadataProvider] can also be invoked multiple times.
     */
    fun <T> startCaching(
        envelopeProvider: () -> Envelope<T>,
        metadataProvider: () -> StoredTelemetryMetadata
    ): String

    /**
     * Stops caching the attempt matching the given ID.
     *
     * After receiving this call the service will not attempt to persist the payload to disk unless
     * an attempt is already in progress. After any attempts to persist have completed the service
     * will delete any cached data associated with the given ID.
     */
    fun stopCaching(id: String)
}
