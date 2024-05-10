package io.embrace.android.embracesdk.capture.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.net.Inet4Address
import java.net.NetworkInterface

@Suppress("DEPRECATION") // uses deprecated APIs for backwards compat
internal class EmbraceNetworkConnectivityService(
    private val context: Context,
    private val clock: Clock,
    private val backgroundWorker: BackgroundWorker,
    private val logger: EmbLogger,
    private val connectivityManager: ConnectivityManager?,
    private val dataSourceModuleProvider: Provider<DataSourceModule?>,
) : BroadcastReceiver(), NetworkConnectivityService {

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    private var lastNetworkStatus: NetworkStatus? = null
    private val networkConnectivityListeners = mutableListOf<NetworkConnectivityListener>()
    override val ipAddress by lazy { calculateIpAddress() }

    init {
        registerConnectivityActionReceiver()
    }

    override fun onReceive(context: Context, intent: Intent) = handleNetworkStatus(true)

    override fun networkStatusOnSessionStarted(startTime: Long) = handleNetworkStatus(false, startTime)

    private fun handleNetworkStatus(notifyListeners: Boolean, timestamp: Long = clock.now()) {
        try {
            val networkStatus = getCurrentNetworkStatus()
            if (didNetworkStatusChange(networkStatus)) {
                lastNetworkStatus = networkStatus

                dataSourceModuleProvider()
                    ?.networkStatusDataSource
                    ?.dataSource
                    ?.networkStatusChange(networkStatus, timestamp)

                if (notifyListeners) {
                    logger.logInfo("Network status changed to: " + networkStatus.name)
                    notifyNetworkConnectivityListeners(networkStatus)
                }
            }
        } catch (ex: Exception) {
            logger.logDebug("Failed to record network connectivity", ex)
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
            networkStatus = NetworkStatus.UNKNOWN
        }
        return networkStatus
    }

    private fun didNetworkStatusChange(newNetworkStatus: NetworkStatus) =
        lastNetworkStatus == null || lastNetworkStatus != newNetworkStatus

    private fun registerConnectivityActionReceiver() {
        backgroundWorker.submit {
            try {
                context.registerReceiver(this, intentFilter)
            } catch (ex: Exception) {
                logger.logDebug(
                    "Failed to register EmbraceNetworkConnectivityService " +
                        "broadcast receiver. Connectivity status will be unavailable.",
                    ex
                )
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
