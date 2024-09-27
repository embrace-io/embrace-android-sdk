package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService

/**
 * This service is responsible for scheduling HTTP requests to the Embrace backend.
 */
interface SchedulingService : Shutdownable {

    /**
     * Called when a new payload has been stored by the [IntakeService] and is ready for scheduling.
     */
    fun onPayloadIntake()
}

class NoopSchedulingService : SchedulingService {
    override fun onPayloadIntake() { }

    override fun shutdown() { }
}
