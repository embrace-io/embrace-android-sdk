package io.embrace.android.embracesdk.fakes

import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

class FakeURLStreamHandler : URLStreamHandler() {
    override fun openConnection(url: URL): URLConnection = FakeHttpsURLConnection(url)
}
