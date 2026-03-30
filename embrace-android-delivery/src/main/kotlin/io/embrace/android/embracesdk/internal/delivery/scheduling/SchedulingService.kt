package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService

/**
 * This service is responsible for scheduling HTTP requests to the Embrace backend.
 */
interface SchedulingService : Shutdownable, NetworkConnectivityListener {

    /**
     * Called when a new payload has been stored by the [IntakeService] and is ready for scheduling.
     */
    fun onPayloadIntake()

    /**
     * Called when payload resurrection has completed. This allows the scheduling service to coordinate with the resurrection service
     * to ensure the payload queue is fully populated before scheduling decisions are made.
     */
    fun onResurrectionComplete()
}
