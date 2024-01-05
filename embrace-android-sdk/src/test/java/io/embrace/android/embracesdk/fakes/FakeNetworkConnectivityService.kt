package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.payload.Interval

internal class FakeNetworkConnectivityService(
    initialNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN,
    override var ipAddress: String = defaultIpAddress
) : FakeDataCaptureService<Interval>(), NetworkConnectivityService {

    private val networkConnectivityListeners = mutableListOf<NetworkConnectivityListener>()
    var networkStatus: NetworkStatus = initialNetworkStatus
        set(value) {
            field = value
            notifyListeners()
        }

    override fun networkStatusOnSessionStarted(startTime: Long) {
        notifyListeners()
    }

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.add(listener)
    }

    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.remove(listener)
    }

    override fun getCurrentNetworkStatus(): NetworkStatus = networkStatus

    override fun close() {
    }

    private fun notifyListeners() {
        networkConnectivityListeners.forEach {
            it.onNetworkConnectivityStatusChanged(networkStatus)
        }
    }

    companion object {
        private const val defaultIpAddress = "220.1.1.1"
    }
}
