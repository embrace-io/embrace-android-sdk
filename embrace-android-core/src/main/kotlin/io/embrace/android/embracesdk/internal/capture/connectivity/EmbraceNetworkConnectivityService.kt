package io.embrace.android.embracesdk.internal.capture.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.net.Inet4Address
import java.net.NetworkInterface

@Suppress("DEPRECATION") // uses deprecated APIs for backwards compat
internal class EmbraceNetworkConnectivityService(
    private val context: Context,
    private val backgroundWorker: BackgroundWorker,
    private val logger: EmbLogger,
    private val connectivityManager: ConnectivityManager?,
) : BroadcastReceiver(), NetworkConnectivityService {

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    private var lastNetworkStatus: NetworkStatus = NetworkStatus.UNKNOWN
    private val networkConnectivityListeners = mutableListOf<NetworkConnectivityListener>()
    override val ipAddress: String? by lazy { calculateIpAddress() }

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

    override fun getCurrentNetworkStatus(): NetworkStatus {
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
        } catch (e: java.lang.Exception) {
            logger.logError("Error while trying to get connectivity status.", e)
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

    private fun calculateIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (ex: Exception) {
            logger.logDebug("Cannot get IP Address")
        }
        return null
    }
}
