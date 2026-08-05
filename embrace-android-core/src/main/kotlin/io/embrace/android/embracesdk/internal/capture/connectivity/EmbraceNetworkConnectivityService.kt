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
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION") // uses deprecated APIs for backwards compat
internal class EmbraceNetworkConnectivityService(
    private val context: Context,
    private val backgroundWorker: BackgroundWorker,
    private val logger: InternalLogger,
    private val connectivityManager: Lazy<ConnectivityManager?>,
) : BroadcastReceiver(), NetworkConnectivityService {

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    private var lastConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Unverified
    private val networkConnectivityListeners = CopyOnWriteArrayList<NetworkConnectivityListener>()
    private val registered = AtomicBoolean(false)

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val connectivityStatus = getCurrentConnectivityStatus()
            if (lastConnectivityStatus != connectivityStatus) {
                lastConnectivityStatus = connectivityStatus
                notifyNetworkConnectivityListeners(connectivityStatus)
            }
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.NetworkStatusCaptureFail, ex)
        }
    }

    private fun getCurrentConnectivityStatus(): ConnectivityStatus {
        return try {
            // if ConnectivityManager is unavailable, assume the network is reachable
            val manager = connectivityManager.value ?: return ConnectivityStatus.Unverified
            val networkInfo = manager.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
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
            logger.trackInternalError(InternalErrorType.NetworkStatusCaptureFail, e)
            OptimisticUnknown
        }
    }

    override fun register() {
        backgroundWorker.submit {
            if (connectivityManager.value != null && !registered.getAndSet(true)) {
                runCatching {
                    context.registerReceiver(this, intentFilter)
                }.onFailure {
                    registered.set(false)
                }
            }
        }
    }

    override fun close() {
        if (registered.getAndSet(false)) {
            runCatching {
                context.unregisterReceiver(this)
            }
        }
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
