package io.embrace.android.embracesdk.internal.capture.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("DEPRECATION") // uses deprecated APIs for backwards compat
internal class EmbraceNetworkConnectivityService(
    private val context: Context,
    private val backgroundWorker: BackgroundWorker,
    private val logger: InternalLogger,
    private val connectivityManager: ConnectivityManager?,
) : BroadcastReceiver(), NetworkConnectivityService {

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN
    private val networkConnectivityListeners = CopyOnWriteArrayList<NetworkConnectivityListener>()

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val networkStatus = getCurrentNetworkStatus()
            if (didNetworkStatusChange(networkStatus)) {
                lastNetworkStatus = networkStatus
                notifyNetworkConnectivityListeners(networkStatus)
            }
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.NETWORK_STATUS_CAPTURE_FAIL, ex)
        }
    }

    private fun getCurrentNetworkStatus(): NetworkStatus {
        var networkStatus: NetworkStatus
        try {
            val networkInfo = connectivityManager?.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
                // Network is reachable
                when (networkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> {
                        networkStatus = NetworkStatus.WIFI
                    }

                    ConnectivityManager.TYPE_MOBILE -> {
                        networkStatus = NetworkStatus.WAN
                    }

                    else -> {
                        networkStatus = NetworkStatus.UNKNOWN
                    }
                }
            } else {
                // Network is not reachable
                networkStatus = NetworkStatus.NOT_REACHABLE
            }
        } catch (e: Exception) {
            logger.trackInternalError(InternalErrorType.NETWORK_STATUS_CAPTURE_FAIL, e)
            networkStatus = NetworkStatus.UNKNOWN
        }
        return networkStatus
    }

    private fun didNetworkStatusChange(newNetworkStatus: NetworkStatus) =
        lastNetworkStatus != newNetworkStatus

    override fun register() {
        backgroundWorker.submit {
            runCatching {
                context.registerReceiver(this, intentFilter)
            }
        }
    }

    override fun close() {
        context.unregisterReceiver(this)
    }

    /**
     * Adds a listener for changes in the connectivity status.
     */
    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.add(listener)
        listener.onNetworkConnectivityStatusChanged(lastNetworkStatus)
    }

    /**
     * Removes a listener for changes in the connectivity status.
     */
    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.remove(listener)
    }

    private fun notifyNetworkConnectivityListeners(status: NetworkStatus) {
        for (listener in networkConnectivityListeners) {
            listener.onNetworkConnectivityStatusChanged(status)
        }
    }
}
