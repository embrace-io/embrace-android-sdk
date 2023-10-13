package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.payload.Interval

internal class FakeNetworkConnectivityService : FakeDataCaptureService<Interval>(), NetworkConnectivityService {

    override fun networkStatusOnSessionStarted(startTime: Long) {
        TODO("Not yet implemented")
    }

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        TODO("Not yet implemented")
    }

    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        TODO("Not yet implemented")
    }

    override fun getCurrentNetworkStatus(): NetworkStatus {
        TODO("Not yet implemented")
    }

    override val ipAddress: String
        get() = TODO("Not yet implemented")

    override fun close() {
    }
}
