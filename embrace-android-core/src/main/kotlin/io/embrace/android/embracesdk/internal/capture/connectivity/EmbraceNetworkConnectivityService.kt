package io.embrace.android.embracesdk.internal.capture.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
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
    private var lastConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Unverified
    private val networkConnectivityListeners = CopyOnWriteArrayList<NetworkConnectivityListener>()

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val connectivityStatus = getCurrentConnectivityStatus()
            if (lastConnectivityStatus != connectivityStatus) {
                lastConnectivityStatus = connectivityStatus
                notifyNetworkConnectivityListeners(connectivityStatus)
            }
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.NETWORK_STATUS_CAPTURE_FAIL, ex)
        }
    }

    private fun getCurrentConnectivityStatus(): ConnectivityStatus {
        var status: ConnectivityStatus
        try {
            val networkInfo = connectivityManager?.activeNetworkInfo
            status = if (networkInfo != null && networkInfo.isConnected) {
                // Network is reachable
                when (networkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> OptimisticWifi
                    ConnectivityManager.TYPE_MOBILE -> OptimisticWan
                    else -> OptimisticUnknown
                }
            } else {
                // Network is not reachable
                ConnectivityStatus.None
            }
        } catch (e: Exception) {
            logger.trackInternalError(InternalErrorType.NETWORK_STATUS_CAPTURE_FAIL, e)
            status = OptimisticUnknown
        }
        return status
    }

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
        listener.onNetworkConnectivityStatusChanged(lastConnectivityStatus)
    }

    /**
     * Removes a listener for changes in the connectivity status.
     */
    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {
        networkConnectivityListeners.remove(listener)
    }

    private fun notifyNetworkConnectivityListeners(status: ConnectivityStatus) {
        for (listener in networkConnectivityListeners) {
            listener.onNetworkConnectivityStatusChanged(status)
        }
    }
}
