package io.embrace.android.embracesdk.instrumentation.huc

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

internal open class FakeURLStreamHandler : URLStreamHandler() {

    override fun openConnection(url: URL): URLConnection {
        return TestURLConnection(url)
    }

    private open class TestURLConnection(url: URL) : HttpURLConnection(url) {
        override fun disconnect() {
        }

        override fun usingProxy(): Boolean {
            return false
        }

        override fun connect() {
        }
    }
}
