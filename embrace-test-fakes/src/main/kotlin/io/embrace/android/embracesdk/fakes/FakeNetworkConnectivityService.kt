package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

public class FakeNetworkConnectivityService(
    initialNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN,
    override var ipAddress: String = defaultIpAddress
) : FakeDataCaptureService<FakeNetworkConnectivityService.Interval>(), NetworkConnectivityService {

    public data class Interval(
        val startTime: Long,
        val endTime: Long,
        val value: String? = null
    )

    private val networkConnectivityListeners = mutableListOf<NetworkConnectivityListener>()
    public var networkStatus: NetworkStatus = initialNetworkStatus
        set(value) {
            field = value
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

    override fun register() {
    }

    private fun notifyListeners() {
        networkConnectivityListeners.forEach {
            it.onNetworkConnectivityStatusChanged(networkStatus)
        }
    }

    private companion object {
        private const val defaultIpAddress = "220.1.1.1"
    }
}

public class NoOpNetworkConnectivityService : NetworkConnectivityService {
    override fun close() {}

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {}

    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {}

    override fun getCurrentNetworkStatus(): NetworkStatus {
        return NetworkStatus.UNKNOWN
    }

    override fun register() {
    }

    override val ipAddress: String? = null
}
