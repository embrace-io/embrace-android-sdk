package io.embrace.android.embracesdk.comms.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

internal class EmbraceUrl(val url: URL) {
    @Throws(IOException::class)
    fun openConnection(): EmbraceConnection {
        return EmbraceConnectionImpl(url.openConnection() as HttpURLConnection, this)
    }

    fun endpoint(): Endpoint {
        return when (url.path.substringAfterLast("/")) {
            Endpoint.EVENTS.path -> Endpoint.EVENTS
            Endpoint.LOGGING.path -> Endpoint.LOGGING
            Endpoint.LOGS.path -> Endpoint.LOGS
            Endpoint.NETWORK.path -> Endpoint.NETWORK
            Endpoint.SESSIONS.path -> Endpoint.SESSIONS
            Endpoint.SESSIONS_V2.path -> Endpoint.SESSIONS_V2
            else -> Endpoint.UNKNOWN
        }
    }

    override fun toString(): String {
        return url.toString()
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbraceUrl

        if (url != other.url) return false

        return true
    }

    companion object {

        @Throws(MalformedURLException::class)
        fun create(url: String): EmbraceUrl = EmbraceUrl(url = URL(url))
    }
}
