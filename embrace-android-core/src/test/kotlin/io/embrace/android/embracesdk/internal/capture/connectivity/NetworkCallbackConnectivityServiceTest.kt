@file:Suppress("DEPRECATION") // NetworkInfo and related APIs are deprecated but needed for ShadowNetworkInfo

package io.embrace.android.embracesdk.internal.capture.connectivity

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.N])
internal class NetworkCallbackConnectivityServiceTest {

    private lateinit var service: NetworkCallbackConnectivityService
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager
    private lateinit var receivedConnectivityStatuses: MutableList<ConnectivityStatus>

    private val networkConnectivityListener = object : NetworkConnectivityListener {
        override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        }

        override fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus) {
            receivedConnectivityStatuses.add(status)
        }
    }

    @Before
    fun setUp() {
        connectivityManager = getApplicationContext<Application>().getSystemService(ConnectivityManager::class.java)
        shadowConnectivityManager = Shadows.shadowOf(connectivityManager).apply {
            setActiveNetworkInfo(null)
        }
        receivedConnectivityStatuses = mutableListOf()
        service = NetworkCallbackConnectivityService(
            fakeBackgroundWorker(),
            InternalLoggerImpl(),
            connectivityManager,
        )
    }

    @Test
    fun `register calls registerDefaultNetworkCallback with service as callback`() {
        service.register()
        assertTrue(shadowConnectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `adding listener before registration`() {
        service.addNetworkConnectivityListener(networkConnectivityListener)
        service.register()
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.single())
    }

    @Test
    fun `adding listener after registration`() {
        service.register()
        service.addNetworkConnectivityListener(networkConnectivityListener)
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.single())
    }

    @Test
    fun `exception during register is caught and does not propagate`() {
        NetworkCallbackConnectivityService(
            fakeBackgroundWorker(),
            InternalLoggerImpl(),
            connectivityManager = null,
        ).apply {
            register()
        }
    }

    @Test
    fun `onAvailable makes doesn't change connectivity status but onCapabilitiesChanged does`() {
        initService()
        val newNetwork = setActiveNetwork(connectedWifi)
        service.onAvailable(newNetwork)
        assertEquals(unverified.getConnectivityStatus(), receivedConnectivityStatuses.single())
        service.onCapabilitiesChanged(newNetwork, connectedWifi.getNetworkCapabilities())
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(connectedWifi.getConnectivityStatus(), receivedConnectivityStatuses.last())
    }

    @Test
    fun `switching from a connected to a disconnected network of the same type will update listener`() {
        initWithNetwork(connectedWifi)
        assertEquals(connectedWifi.getConnectivityStatus(), receivedConnectivityStatuses.single())
        val newerNetwork = setActiveNetwork(disconnectedWifi)
        service.onAvailable(newerNetwork)
        service.onCapabilitiesChanged(newerNetwork, disconnectedWifi.getNetworkCapabilities())
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(disconnectedWifi.getConnectivityStatus(), receivedConnectivityStatuses.last())
    }

    @Test
    fun `switching to all the connections`() {
        initService()
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.last())
        networkCapabilities.keys.forEach { networkInfo ->
            setActiveNetwork(networkInfo).let { network ->
                service.onAvailable(network)
                service.onCapabilitiesChanged(network, networkInfo.getNetworkCapabilities())
            }
            assertEquals(networkInfo.getConnectivityStatus(), receivedConnectivityStatuses.last())
        }
    }

    @Test
    fun `switching from unverified to connected unknown will result in a dispatch`() {
        initService()
        assertEquals(ConnectivityStatus.Unverified, receivedConnectivityStatuses.last())
        setActiveNetwork(connectedUnknown).let { network ->
            service.onAvailable(network)
            service.onCapabilitiesChanged(network, connectedUnknown.getNetworkCapabilities())
        }
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(connectedUnknown.getConnectivityStatus(), receivedConnectivityStatuses.last())
    }

    @Test
    fun `onCapabilitiesChanged for non-current network does not dispatch`() {
        val originalNetwork = initWithNetwork(connectedWifi)
        service.onAvailable(originalNetwork)
        assertNotEquals(originalNetwork, setActiveNetwork(connectedWan))
        service.onCapabilitiesChanged(originalNetwork, connectedWifi.getNetworkCapabilities())
        assertEquals(connectedWifi.getConnectivityStatus(), receivedConnectivityStatuses.single())
    }

    @Test
    fun `onLost for current network will switch connectivity status to none`() {
        val network = initWithNetwork(connectedWifi)
        service.onLost(network)
        assertEquals(2, receivedConnectivityStatuses.size)
        assertEquals(none.getConnectivityStatus(), receivedConnectivityStatuses.last())
    }

    @Test
    fun `onLost for current network make subsequent capability changes for that network not be processed`() {
        initWithNetwork(connectedWifi)
        val newNetwork = setActiveNetwork(connectedWan)
        service.onAvailable(newNetwork)
        service.onLost(newNetwork)
        service.onCapabilitiesChanged(newNetwork, connectedWan.getNetworkCapabilities())
        assertEquals(none.getConnectivityStatus(), receivedConnectivityStatuses.last())
    }

    @Test
    fun `onLost for non-current network is ignored`() {
        val oldNetwork = initWithNetwork(connectedWifi)
        val newNetwork = setActiveNetwork(connectedWan)
        service.onAvailable(newNetwork)
        service.onLost(oldNetwork)
        assertEquals(connectedWifi.getConnectivityStatus(), receivedConnectivityStatuses.last())
        service.onCapabilitiesChanged(newNetwork, connectedWan.getNetworkCapabilities())
        assertEquals(connectedWan.getConnectivityStatus(), receivedConnectivityStatuses.last())
    }

    @Test
    fun `switching to the same or different networks with equivalent connectivity status will not result in multiple dispatches`() {
        initWithNetwork(connectedWifi)
        val newWifi = setActiveNetwork(connectedWifi)
        service.onAvailable(newWifi)
        service.onCapabilitiesChanged(newWifi, connectedWifi.getNetworkCapabilities())
        service.onCapabilitiesChanged(newWifi, connectedWifi.getNetworkCapabilities())
        assertEquals(1, receivedConnectivityStatuses.size)
    }

    @Test
    fun `addNetworkConnectivityListener gets current status upon addition`() {
        val newNetwork = setActiveNetwork(connectedWifi)
        service.register()
        service.onAvailable(newNetwork)
        service.onCapabilitiesChanged(newNetwork, connectedWifi.getNetworkCapabilities())
        service.addNetworkConnectivityListener(networkConnectivityListener)
        assertEquals(connectedWifi.getConnectivityStatus(), receivedConnectivityStatuses.single())
    }

    @Test
    fun `removeNetworkConnectivityListener stops updates`() {
        initService()
        service.removeNetworkConnectivityListener(networkConnectivityListener)
        val newNetwork = setActiveNetwork(connectedWifi)
        service.onAvailable(newNetwork)
        service.onCapabilitiesChanged(newNetwork, connectedWifi.getNetworkCapabilities())
        assertEquals(unverified.getConnectivityStatus(), receivedConnectivityStatuses.single())
    }

    @Test
    fun `close calls unregisterNetworkCallback with service as callback`() {
        service.register()
        assertTrue(shadowConnectivityManager.networkCallbacks.contains(service))
        service.close()
        assertFalse(shadowConnectivityManager.networkCallbacks.contains(service))
    }

    @Test
    fun `legacy listener receives bridged NetworkStatus via default overload`() {
        val receivedStatuses = mutableListOf<NetworkStatus>()
        val legacyListener = object : NetworkConnectivityListener {
            override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
                receivedStatuses.add(status)
            }
        }
        service.register()
        service.addNetworkConnectivityListener(legacyListener)
        val newNetwork = setActiveNetwork(connectedWifi)
        service.onAvailable(newNetwork)
        service.onCapabilitiesChanged(newNetwork, connectedWifi.getNetworkCapabilities())
        assertEquals(listOf(NetworkStatus.UNKNOWN, NetworkStatus.WIFI), receivedStatuses)
    }

    private fun initWithNetwork(network: NetworkInfo): Network {
        val newNetwork = setActiveNetwork(network)
        initService()
        return newNetwork
    }

    private fun initService() {
        service.register()
        service.addNetworkConnectivityListener(networkConnectivityListener)
    }

    private fun setActiveNetwork(networkInfo: NetworkInfo): Network {
        shadowConnectivityManager.setActiveNetworkInfo(networkInfo)
        val activeNetwork = checkNotNull(connectivityManager.activeNetwork)
        shadowConnectivityManager.setNetworkCapabilities(activeNetwork, networkCapabilities[networkInfo])
        return activeNetwork
    }

    private fun NetworkInfo.getNetworkCapabilities(): NetworkCapabilities = checkNotNull(networkCapabilities[this])

    private fun NetworkInfo.getConnectivityStatus(): ConnectivityStatus = checkNotNull(connectivityStatuses[this])

    private companion object {
        val connectedWifi = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.CONNECTED,
            type = ConnectivityManager.TYPE_WIFI,
            isAvailable = true,
            isConnected = true,
        )

        val disconnectedWifi = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.DISCONNECTED,
            type = ConnectivityManager.TYPE_WIFI,
            isAvailable = true,
            isConnected = false,
        )

        val connectedWan = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.CONNECTED,
            type = ConnectivityManager.TYPE_MOBILE,
            isAvailable = true,
            isConnected = true,
        )

        val disconnectedWan = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.DISCONNECTED,
            type = ConnectivityManager.TYPE_MOBILE,
            isAvailable = true,
            isConnected = false,
        )

        val connectedUnknown = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.CONNECTED,
            type = ConnectivityManager.TYPE_ETHERNET,
            isAvailable = true,
            isConnected = true,
        )

        val disconnectedUnknown = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.DISCONNECTED,
            type = ConnectivityManager.TYPE_ETHERNET,
            isAvailable = true,
            isConnected = false,
        )

        val none = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.BLOCKED,
            type = ConnectivityManager.TYPE_DUMMY,
            isAvailable = false,
            isConnected = false,
        )

        val unverified = createNetworkInfo(
            detailedState = NetworkInfo.DetailedState.CONNECTED,
            type = ConnectivityManager.TYPE_DUMMY,
            isAvailable = true,
            isConnected = true,
        )

        val networkCapabilities = mapOf(
            connectedWifi to buildCaps {
                addCapability(NET_CAPABILITY_INTERNET)
                addCapability(NET_CAPABILITY_VALIDATED)
                addTransportType(TRANSPORT_WIFI)
            },
            disconnectedWifi to buildCaps {
                addCapability(NET_CAPABILITY_INTERNET)
                addTransportType(TRANSPORT_WIFI)
            },
            connectedWan to buildCaps {
                addCapability(NET_CAPABILITY_INTERNET)
                addCapability(NET_CAPABILITY_VALIDATED)
                addTransportType(TRANSPORT_CELLULAR)
            },
            disconnectedWan to buildCaps {
                addCapability(NET_CAPABILITY_INTERNET)
                addTransportType(TRANSPORT_CELLULAR)
            },
            connectedUnknown to buildCaps {
                addCapability(NET_CAPABILITY_INTERNET)
                addCapability(NET_CAPABILITY_VALIDATED)
            },
            disconnectedUnknown to buildCaps {
                addCapability(NET_CAPABILITY_INTERNET)
            },
            none to buildCaps { }
        )

        val connectivityStatuses = mapOf(
            connectedWifi to ConnectivityStatus.Wifi(true),
            disconnectedWifi to ConnectivityStatus.Wifi(false),
            connectedWan to ConnectivityStatus.Wan(true),
            disconnectedWan to ConnectivityStatus.Wan(false),
            connectedUnknown to ConnectivityStatus.Unknown(true),
            disconnectedUnknown to ConnectivityStatus.Unknown(false),
            unverified to ConnectivityStatus.Unverified,
            none to ConnectivityStatus.None
        )

        private fun createNetworkInfo(
            detailedState: NetworkInfo.DetailedState,
            type: Int,
            isAvailable: Boolean,
            isConnected: Boolean,
        ): NetworkInfo = checkNotNull(ShadowNetworkInfo.newInstance(detailedState, type, 0, isAvailable, isConnected))

        private fun buildCaps(configure: ShadowNetworkCapabilities.() -> Unit): NetworkCapabilities {
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadow.extract<ShadowNetworkCapabilities>(caps).configure()
            return caps
        }
    }
}
