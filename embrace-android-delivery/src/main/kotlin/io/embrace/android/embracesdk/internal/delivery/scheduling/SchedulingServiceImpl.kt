package io.embrace.android.embracesdk.internal.delivery.scheduling

import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService

internal class SchedulingServiceImpl(
    @Suppress("unused") private val requestExecutionService: RequestExecutionService
) : SchedulingService {

    override fun handleCrash(crashId: String) {
    }

    override fun onPayloadIntake() {
    }
}
