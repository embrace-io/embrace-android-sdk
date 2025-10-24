package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService

class FakeSchedulingService : SchedulingService {

    var shutdownCount: Int = 0
    var payloadIntakeCount: Int = 0

    override fun shutdown() {
        shutdownCount++
    }

    override fun onPayloadIntake() {
        payloadIntakeCount++
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
    }
}
