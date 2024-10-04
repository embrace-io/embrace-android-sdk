package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * This service caches in-memory data in case the process terminates. If the process terminates
 * the [PayloadResurrectionService] will convert the cached file into a full payload on next launch
 * that is then processed by the [IntakeService].
 */
interface PayloadCachingService : Shutdownable {

    /**
     * Starts caching a payload.
     */
    fun startCaching(isInBackground: Boolean, supplier: () -> Envelope<SessionPayload>?)

    /**
     * Stops caching a payload.
     */
    fun stopCaching()
}
