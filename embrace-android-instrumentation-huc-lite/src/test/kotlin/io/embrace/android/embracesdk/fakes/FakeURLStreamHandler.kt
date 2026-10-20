package io.embrace.android.embracesdk.fakes

import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class FakeURLStreamHandler : URLStreamHandler() {
    override fun openConnection(url: URL): URLConnection = FakeHttpsURLConnection(url)

    private class FakeHttpsURLConnection(url: URL) : HttpsURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getCipherSuite(): String? = null
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
