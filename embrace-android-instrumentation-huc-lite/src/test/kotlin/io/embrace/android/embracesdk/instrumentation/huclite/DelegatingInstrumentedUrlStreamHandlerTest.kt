package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

internal class DelegatingInstrumentedUrlStreamHandlerTest {

    private val expected = IOException("delegate failed")

    private val url = URL(
        testUrl.protocol,
        testUrl.host,
        testUrl.port,
        testUrl.file,
        DelegatingInstrumentedUrlStreamHandler(
            delegateHandler = ThrowingURLStreamHandler(expected),
            clock = FakeClock(),
            dataSourceProvider = { null },
        ),
    )

    @Test
    fun `openConnection rethrows the delegate exception unwrapped`() {
        val thrown = assertThrows(IOException::class.java) {
            url.openConnection()
        }
        assertSame(expected, thrown)
    }

    @Test
    fun `openConnection with proxy rethrows the delegate exception unwrapped`() {
        val thrown = assertThrows(IOException::class.java) {
            url.openConnection(Proxy.NO_PROXY)
        }
        assertSame(expected, thrown)
    }

    private class ThrowingURLStreamHandler(private val error: IOException) : URLStreamHandler() {
        override fun openConnection(url: URL?): URLConnection = throw error
        override fun openConnection(url: URL?, proxy: Proxy?): URLConnection = throw error
    }
}
