package android.net

class FakeNetwork : Network() {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

class FakeConnectivityManager : ConnectivityManager() {
    var active: Network? = null
    val capabilities: MutableMap<Network, NetworkCapabilities> = mutableMapOf()
    val networkCallbacks: MutableList<NetworkCallback> = mutableListOf()

    override fun getActiveNetwork(): Network? = active

    override fun getNetworkCapabilities(network: Network?): NetworkCapabilities? = capabilities[network]

    override fun registerDefaultNetworkCallback(networkCallback: NetworkCallback) {
        networkCallbacks.add(networkCallback)
    }

    override fun unregisterNetworkCallback(networkCallback: NetworkCallback) {
        networkCallbacks.remove(networkCallback)
    }
}
