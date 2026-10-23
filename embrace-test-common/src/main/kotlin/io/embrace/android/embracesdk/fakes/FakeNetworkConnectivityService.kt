package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import java.util.concurrent.CopyOnWriteArrayList

class FakeNetworkConnectivityService(
    initialConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Unverified,
    private val executor: BlockingScheduledExecutorService? = null,
) : NetworkConnectivityService {

    private val networkConnectivityListeners = CopyOnWriteArrayList<NetworkConnectivityListener>()
    val listeners: List<NetworkConnectivityListener> get() = networkConnectivityListeners
    var connectivityStatus: ConnectivityStatus = initialConnectivityStatus
        set(value) {
            field = value
            notifyListeners()
        }

    /**
     * When set, adding a listener throws this instead.
     */
    var addListenerFailure: Throwable? = null

    var registered: Boolean = false
        private set

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        addListenerFailure?.let { throw it }
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
        registered = true
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
