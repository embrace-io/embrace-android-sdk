package io.embrace.android.embracesdk.internal.capture.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("DEPRECATION") // uses deprecated APIs for backwards compat
internal class EmbraceNetworkConnectivityService(
    private val context: Context,
    private val backgroundWorker: BackgroundWorker,
    private val errorHandler: ErrorHandler,
    private val connectivityManager: Lazy<ConnectivityManager?>,
) : BroadcastReceiver(), NetworkConnectivityService {

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    private var lastConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Unverified
    private val networkConnectivityListeners = CopyOnWriteArrayList<NetworkConnectivityListener>()
    private val lock = Any()
    private var registered = false
    private var closed = false

    override fun onReceive(context: Context, intent: Intent) {
        errorHandler.guardSdkCode(SdkErrorType.NetworkStatusCaptureFail) {
            val connectivityStatus = getCurrentConnectivityStatus()
            if (lastConnectivityStatus != connectivityStatus) {
                lastConnectivityStatus = connectivityStatus
                notifyNetworkConnectivityListeners(connectivityStatus)
            }
        }
    }

    private fun getCurrentConnectivityStatus(): ConnectivityStatus {
        return errorHandler.guardSdkCode(SdkErrorType.NetworkStatusCaptureFail, OptimisticUnknown) {
            // if ConnectivityManager is unavailable, assume the network is reachable
            val manager = connectivityManager.value ?: return@guardSdkCode ConnectivityStatus.Unverified
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
        }
    }

    override fun register() {
        backgroundWorker.submit {
            if (connectivityManager.value != null) {
                synchronized(lock) {
                    if (!closed && !registered) {
                        errorHandler.guardSdkCode(SdkErrorType.ConnectivityUpdateFailure) {
                            context.registerReceiver(this, intentFilter)
                            registered = true
                        }
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
                    context.unregisterReceiver(this)
                }
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
