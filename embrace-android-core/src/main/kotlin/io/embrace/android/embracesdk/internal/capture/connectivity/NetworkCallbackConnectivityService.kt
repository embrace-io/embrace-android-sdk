package io.embrace.android.embracesdk.internal.capture.connectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.N)
internal class NetworkCallbackConnectivityService(
    private val backgroundWorker: BackgroundWorker,
    private val logger: InternalLogger,
    private val connectivityManager: ConnectivityManager?,
) : NetworkConnectivityService, ConnectivityManager.NetworkCallback() {

    private var currentNetwork: AtomicReference<Network?> = AtomicReference(null)
    private val currentStatus = AtomicReference<ConnectivityStatus>(UNVERIFIED)
    private val listeners = CopyOnWriteArrayList<NetworkConnectivityListener>()

    override fun onAvailable(network: Network) {
        updateNetwork(network)
    }

    override fun onLost(network: Network) {
        if (network == currentNetwork.get()) {
            updateNetwork(null)
            updateStatus(null, null)
        }
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        updateStatus(network, networkCapabilities)
    }

    override fun register() {
        backgroundWorker.submit {
            updateSafely {
                connectivityManager?.activeNetwork?.let { defaultNetwork ->
                    synchronized(currentNetwork) {
                        updateNetwork(defaultNetwork)
                        updateStatus(
                            updatedNetwork = defaultNetwork,
                            networkCapabilities = connectivityManager.getNetworkCapabilities(defaultNetwork)
                        )
                    }
                }
                connectivityManager?.registerDefaultNetworkCallback(this)
            }
        }
    }

    override fun close() {
        runCatching {
            connectivityManager?.unregisterNetworkCallback(this)
        }
    }

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        updateSafely {
            listeners.add(listener)
            listener.onNetworkConnectivityStatusChanged(currentStatus.get())
        }
    }

    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        listeners.remove(listener)
    }

    private fun updateNetwork(newNetwork: Network?) {
        synchronized(currentNetwork) {
            currentNetwork.set(newNetwork)
        }
    }

    private fun updateStatus(updatedNetwork: Network?, networkCapabilities: NetworkCapabilities?) {
        if (updatedNetwork == currentNetwork.get()) {
            val newStatus = networkCapabilities?.toConnectionStatus() ?: ConnectivityStatus.None
            if (currentStatus.get() != newStatus) {
                currentStatus.set(newStatus)
                for (listener in listeners) {
                    updateSafely {
                        listener.onNetworkConnectivityStatusChanged(newStatus)
                    }
                }
            }
        }
    }

    private fun NetworkCapabilities.toConnectionStatus(): ConnectivityStatus {
        val canConnect = hasCapability(NET_CAPABILITY_INTERNET)
        val connected = hasCapability(NET_CAPABILITY_VALIDATED)
        return when {
            !canConnect -> ConnectivityStatus.None
            hasTransport(TRANSPORT_WIFI) -> ConnectivityStatus.Wifi(connected)
            hasTransport(TRANSPORT_CELLULAR) -> ConnectivityStatus.Wan(connected)
            else -> ConnectivityStatus.Unknown(connected)
        }
    }

    private fun updateSafely(action: () -> Unit) =
        runCatching {
            action()
        }.onFailure {
            logger.trackInternalError(InternalErrorType.CONNECTIVITY_UPDATE_FAILURE, it)
        }

    private companion object {
        /**
         * When the type of connection is not known, but it's assumed it's connected to the internet
         */
        val UNVERIFIED = ConnectivityStatus.Unknown(true)
    }
}
