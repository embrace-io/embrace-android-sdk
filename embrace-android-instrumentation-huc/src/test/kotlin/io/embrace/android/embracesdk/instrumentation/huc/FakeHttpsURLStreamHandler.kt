package io.embrace.android.embracesdk.instrumentation.huc

import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

internal class FakeHttpsURLStreamHandler : URLStreamHandler() {

    override fun openConnection(url: URL): URLConnection = TestHttpsURLConnection(url)

    override fun openConnection(url: URL, proxy: Proxy): URLConnection = openConnection(url)

    private class TestHttpsURLConnection(url: URL) : HttpsURLConnection(url) {
        override fun disconnect() {
        }

        override fun usingProxy(): Boolean = false

        override fun connect() {
        }

        override fun getCipherSuite(): String = ""

        override fun getLocalCertificates(): Array<Certificate> = emptyArray()

        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
