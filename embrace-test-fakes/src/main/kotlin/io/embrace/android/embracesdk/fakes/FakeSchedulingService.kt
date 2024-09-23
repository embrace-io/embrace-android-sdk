package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService

class FakeSchedulingService : SchedulingService {

    var crashId: String? = null
    var payloadIntakeCount: Int = 0

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun onPayloadIntake() {
        payloadIntakeCount++
    }
}
