package io.embrace.android.embracesdk.internal.delivery.resurrection

import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService

class PayloadResurrectionServiceImpl(
    @Suppress("unused") private val intakeService: IntakeService
) : PayloadResurrectionService {

    override fun resurrectOldPayloads() {
    }
}
