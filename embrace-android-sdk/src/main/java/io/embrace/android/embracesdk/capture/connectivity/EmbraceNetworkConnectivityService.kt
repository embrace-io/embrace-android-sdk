package io.embrace.android.embracesdk.capture.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.payload.Interval
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.NavigableMap
import java.util.TreeMap
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

@Suppress("DEPRECATION") // uses deprecated APIs for backwards compat
internal class EmbraceNetworkConnectivityService(
    private val context: Context,
    private val clock: Clock,
    private val registrationExecutorService: ExecutorService,
    private val logger: InternalEmbraceLogger,
    private val connectivityManager: ConnectivityManager?,
    private val isNetworkCaptureEnabled: Boolean
) : BroadcastReceiver(), NetworkConnectivityService {

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    private val networkReachable: NavigableMap<Long, NetworkStatus> = TreeMap()
    private var lastNetworkStatus: NetworkStatus? = null
    private val networkConnectivityListeners = mutableListOf<NetworkConnectivityListener>()
    override val ipAddress by lazy { calculateIpAddress() }

    init {
        registerConnectivityActionReceiver()
    }

    override fun onReceive(context: Context, intent: Intent) = handleNetworkStatus(true)

    override fun getCapturedData(): List<Interval> {
        logger.logDeveloper("EmbraceNetworkConnectivityService", "getNetworkInterfaceIntervals")
        val endTime = clock.now()
        synchronized(this) {
            val results: MutableList<Interval> = ArrayList()
            networkReachable.subMap(0, endTime).forEach { (currentTime, value) ->
                val next = networkReachable.higherKey(currentTime)
                results.add(Interval(currentTime, next ?: endTime, value.value))
            }
            return results
        }
    }

    override fun networkStatusOnSessionStarted(startTime: Long) = handleNetworkStatus(false, startTime)

    private fun handleNetworkStatus(notifyListeners: Boolean, timestamp: Long = clock.now()) {
        try {
            logger.logDeveloper("EmbraceNetworkConnectivityService", "handleNetworkStatus")
            val networkStatus = getCurrentNetworkStatus()
            if (didNetworkStatusChange(networkStatus)) {
                lastNetworkStatus = networkStatus
                if (isNetworkCaptureEnabled) {
                    saveStatus(timestamp, networkStatus)
                }
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
                        logger.logDeveloper(
                            "EmbraceNetworkConnectivityService",
                            "Network connected to WIFI"
                        )
                        networkStatus = NetworkStatus.WIFI
                    }

                    ConnectivityManager.TYPE_MOBILE -> {
                        logger.logDeveloper(
                            "EmbraceNetworkConnectivityService",
                            "Network connected to MOBILE"
                        )
                        networkStatus = NetworkStatus.WAN
                    }

                    else -> {
                        logger.logDeveloper(
                            "EmbraceNetworkConnectivityService",
                            "Network is reachable but type is not WIFI or MOBILE"
                        )
                        networkStatus = NetworkStatus.UNKNOWN
                    }
                }
            } else {
                // Network is not reachable
                logger.logDeveloper("EmbraceNetworkConnectivityService", "Network not reachable")
                networkStatus = NetworkStatus.NOT_REACHABLE
            }
        } catch (e: java.lang.Exception) {
            logger.logError("Error while trying to get connectivity status.", e)
            networkStatus = NetworkStatus.UNKNOWN
        }
        return networkStatus
    }

    private fun saveStatus(timestamp: Long, networkStatus: NetworkStatus) {
        synchronized(this) {
            networkReachable[timestamp] = networkStatus
        }
    }

    private fun didNetworkStatusChange(newNetworkStatus: NetworkStatus) =
        lastNetworkStatus == null || lastNetworkStatus != newNetworkStatus

    private fun registerConnectivityActionReceiver() {
        registrationExecutorService.submit(
            Callable<Any?> {
                try {
                    context.registerReceiver(this, intentFilter)
                } catch (ex: Exception) {
                    logger.logDebug(
                        "Failed to register EmbraceNetworkConnectivityService " +
                            "broadcast receiver. Connectivity status will be unavailable.",
                        ex
                    )
                }
                null
            }
        )
    }

    override fun close() {
        context.unregisterReceiver(this)
        logger.logDeveloper("EmbraceNetworkConnectivityService", "closed")
    }

    override fun cleanCollections() {
        networkReachable.clear()
        logger.logDeveloper("EmbraceNetworkConnectivityService", "Collections cleaned")
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
            logDebug("Cannot get IP Address")
        }
        return null
    }
}
