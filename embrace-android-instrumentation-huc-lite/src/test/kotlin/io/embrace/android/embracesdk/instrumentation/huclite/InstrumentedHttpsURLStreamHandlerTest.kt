package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

internal class InstrumentedHttpsURLStreamHandlerTest {

    private val harness = HucTestHarness()

    @Test
    fun `delegate exception is not tracked`() {
        val url = createUrl(ThrowingURLStreamHandler(IOException("delegate failed")))
        assertThrows(IOException::class.java) { url.openConnection() }
        assertThrows(IOException::class.java) { url.openConnection(Proxy.NO_PROXY) }
        assertTrue(harness.fakeErrorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `delegate returning a non-HTTPS connection is tracked`() {
        val url = createUrl(HttpURLStreamHandler())
        assertThrows(IOException::class.java) { url.openConnection() }
        val error = harness.fakeErrorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.DataSourceDataCaptureFail.toString(), error.msg)
        assertTrue(error.throwable?.cause is ClassCastException)
    }

    private fun createUrl(delegate: URLStreamHandler) = URL(
        testUrl.protocol,
        testUrl.host,
        testUrl.port,
        testUrl.file,
        InstrumentedHttpsURLStreamHandler(
            delegatedHandler = delegate,
            clock = harness.fakeClock,
            dataSourceProvider = { harness.hucLiteDataSource },
        ),
    )

    private class ThrowingURLStreamHandler(private val error: IOException) : URLStreamHandler() {
        override fun openConnection(url: URL?): URLConnection = throw error
        override fun openConnection(url: URL?, proxy: Proxy?): URLConnection = throw error
    }

    private class HttpURLStreamHandler : URLStreamHandler() {
        override fun openConnection(url: URL): URLConnection = object : HttpURLConnection(url) {
            override fun disconnect() {}
            override fun usingProxy(): Boolean = false
            override fun connect() {}
        }
    }
}
