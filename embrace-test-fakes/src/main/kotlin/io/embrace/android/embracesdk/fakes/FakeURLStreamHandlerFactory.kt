package io.embrace.android.embracesdk.fakes

import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

class FakeURLStreamHandlerFactory : URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String?): URLStreamHandler? = null
}
