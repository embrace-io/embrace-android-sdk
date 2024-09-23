package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService

class FakeSchedulingService : SchedulingService {
    override fun handleCrash(crashId: String) {
    }

    override fun onPayloadIntake() {
    }
}
