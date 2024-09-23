package io.embrace.android.embracesdk.internal.delivery.resurrection

import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService

/**
 * This service finds cached payloads from previous process launches & sends them to the
 * [IntakeService]. If the [IntakeService] accepts the cached payload, this service will then
 * delete the cached payload.
 */
interface PayloadResurrectionService {

    /**
     * Resurrects any payloads that were cached in a previous process & sends them to the
     * [IntakeService].
     */
    fun resurrectOldPayloads()
}
