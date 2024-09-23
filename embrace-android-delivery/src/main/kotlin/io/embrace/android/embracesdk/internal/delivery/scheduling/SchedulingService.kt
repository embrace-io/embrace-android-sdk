package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService

/**
 * This service is responsible for scheduling HTTP requests to the Embrace backend.
 */
interface SchedulingService : CrashTeardownHandler {

    /**
     * Called when a new payload has been stored by the [IntakeService] and is ready for scheduling.
     */
    fun onPayloadIntake()
}
