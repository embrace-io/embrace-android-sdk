package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import java.util.concurrent.CopyOnWriteArrayList

class FakeNetworkConnectivityService(
    initialConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Unverified,
    private val executor: BlockingScheduledExecutorService? = null
) : NetworkConnectivityService {

    private val networkConnectivityListeners = CopyOnWriteArrayList<NetworkConnectivityListener>()
    var connectivityStatus: ConnectivityStatus = initialConnectivityStatus
        set(value) {
            field = value
            notifyListeners()
        }

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.add(listener)
        runOnThread {
            listener.onNetworkConnectivityStatusChanged(connectivityStatus)
        }
    }

    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.remove(listener)
    }

    override fun close() {
    }

    override fun register() {
    }

    private fun notifyListeners() {
        runOnThread {
            networkConnectivityListeners.forEach {
                it.onNetworkConnectivityStatusChanged(connectivityStatus)
            }
        }
    }

    private fun runOnThread(action: () -> Unit = {}) {
        if (executor == null) {
            action()
        } else {
            executor.submit {
                action()
            }
        }
    }
}
