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
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.N)
internal class NetworkCallbackConnectivityService(
    private val backgroundWorker: BackgroundWorker,
    private val errorHandler: ErrorHandler,
    private val connectivityManager: Lazy<ConnectivityManager?>,
    private val toStatus: (NetworkCapabilities) -> ConnectivityStatus = {
        connectivityStatus(it::hasCapability, it::hasTransport)
    },
) : NetworkConnectivityService, ConnectivityManager.NetworkCallback() {

    private var currentNetwork: AtomicReference<Network?> = AtomicReference(null)
    private val currentStatus = AtomicReference<ConnectivityStatus>(ConnectivityStatus.Unverified)
    private val listeners = CopyOnWriteArrayList<NetworkConnectivityListener>()
    private val lock = Any()
    private var registered = false
    private var closed = false

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
            val manager = connectivityManager.value ?: return@submit
            synchronized(lock) {
                if (!closed && !registered) {
                    errorHandler.guardSdkCode(SdkErrorType.ConnectivityUpdateFailure) {
                        manager.activeNetwork?.let { defaultNetwork ->
                            synchronized(currentNetwork) {
                                updateNetwork(defaultNetwork)
                                updateStatus(
                                    updatedNetwork = defaultNetwork,
                                    networkCapabilities = manager.getNetworkCapabilities(defaultNetwork),
                                )
                            }
                        }
                        manager.registerDefaultNetworkCallback(this)
                        registered = true
                    }
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            if (registered) {
                registered = false
                errorHandler.guardSdkCode(SdkErrorType.ConnectivityUpdateFailure) {
                    connectivityManager.value?.unregisterNetworkCallback(this)
                }
            }
        }
    }

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        errorHandler.guardSdkCode(SdkErrorType.ConnectivityUpdateFailure) {
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
            val newStatus = networkCapabilities?.let(toStatus) ?: ConnectivityStatus.None
            if (currentStatus.get() != newStatus) {
                currentStatus.set(newStatus)
                for (listener in listeners) {
                    errorHandler.guardSdkCode(SdkErrorType.ConnectivityUpdateFailure) {
                        listener.onNetworkConnectivityStatusChanged(newStatus)
                    }
                }
            }
        }
    }
}

/**
 * Maps a network's capabilities to the [ConnectivityStatus] reported for it.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal fun connectivityStatus(hasCapability: (Int) -> Boolean, hasTransport: (Int) -> Boolean): ConnectivityStatus {
    val canConnect = hasCapability(NET_CAPABILITY_INTERNET)
    val connected = hasCapability(NET_CAPABILITY_VALIDATED)
    return when {
        !canConnect -> ConnectivityStatus.None
        hasTransport(TRANSPORT_WIFI) -> ConnectivityStatus.Wifi(connected)
        hasTransport(TRANSPORT_CELLULAR) -> ConnectivityStatus.Wan(connected)
        else -> ConnectivityStatus.Unknown(connected)
    }
}
