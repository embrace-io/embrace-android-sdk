package io.embrace.android.embracesdk.internal.network.http

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class EmbraceUrlConnectionDelegateDelegationTest {
    private lateinit var mockConnection: HttpsURLConnection
    private lateinit var connectionDelegate: EmbraceUrlConnectionDelegate<HttpsURLConnection>

    @Before
    fun setup() {
        mockConnection = mockk(relaxed = true)
        connectionDelegate = EmbraceUrlConnectionDelegate<HttpsURLConnection>(mockConnection, true)
    }

    @Test
    fun testAddRequestProperty() {
        connectionDelegate.addRequestProperty("test", "testValue")
        verify(exactly = 1) { mockConnection.addRequestProperty("test", "testValue") }
    }

    @Test
    fun testConnect() {
        connectionDelegate.connect()
        verify(exactly = 1) { mockConnection.connect() }
    }

    @Test
    fun testDisconnect() {
        connectionDelegate.disconnect()
        verify(exactly = 1) { mockConnection.disconnect() }
    }

    @Test
    fun testGetAllowUserInteraction() {
        connectionDelegate.allowUserInteraction
        verify(exactly = 1) { mockConnection.allowUserInteraction }
    }

    @Test
    fun testSetAllowUserInteraction() {
        connectionDelegate.allowUserInteraction = true
        verify(exactly = 1) { mockConnection.allowUserInteraction = true }
    }

    @Test
    fun testGetConnectTimeout() {
        connectionDelegate.connectTimeout
        verify(exactly = 1) { mockConnection.connectTimeout }
    }

    @Test
    fun testSetConnectTimeout() {
        connectionDelegate.connectTimeout = 5
        verify(exactly = 1) { mockConnection.connectTimeout = 5 }
    }

    @Test
    fun testGetContent() {
        connectionDelegate.content
        verify(exactly = 1) { mockConnection.content }
    }

    @Test
    fun testGetContentWithClasses() {
        val array = arrayOf(Object::class.java)
        connectionDelegate.getContent(array)
        verify(exactly = 1) { mockConnection.getContent(array) }
    }

    @Test
    fun testGetContentEncoding() {
        connectionDelegate.contentEncoding
        verify(exactly = 2) { mockConnection.contentEncoding }
    }

    @Test
    fun testGetContentLength() {
        connectionDelegate.contentLength
        verify(exactly = 1) { mockConnection.contentLength }
    }

    @Test
    fun testGetContentLengthLong() {
        connectionDelegate.contentLengthLong
        verify(exactly = 1) { mockConnection.contentLengthLong }
    }

    @Test
    fun testGetContentType() {
        connectionDelegate.contentType
        verify(exactly = 1) { mockConnection.contentType }
    }

    @Test
    fun testGetDate() {
        connectionDelegate.date
        verify(exactly = 1) { mockConnection.date }
    }

    @Test
    fun testGetDefaultUseCaches() {
        connectionDelegate.defaultUseCaches
        verify(exactly = 1) { mockConnection.defaultUseCaches }
    }

    @Test
    fun testSetDefaultUseCaches() {
        connectionDelegate.defaultUseCaches = false
        verify(exactly = 1) { mockConnection.defaultUseCaches = false }
    }

    @Test
    fun testGetDoInput() {
        connectionDelegate.doInput
        verify(exactly = 1) { mockConnection.doInput }
    }

    @Test
    fun testSetDoInput() {
        connectionDelegate.doInput = true
        verify(exactly = 1) { mockConnection.doInput = true }
    }

    @Test
    fun testGetDoOutput() {
        connectionDelegate.doOutput
        verify(exactly = 1) { mockConnection.doOutput }
    }

    @Test
    fun testSetDoOutput() {
        connectionDelegate.doOutput = true
        verify(exactly = 1) { mockConnection.doOutput = true }
    }

    @Test
    fun testGetErrorStream() {
        connectionDelegate.errorStream
        verify(exactly = 1) { mockConnection.errorStream }
    }

    @Test
    fun testGetHeaderFieldInt() {
        connectionDelegate.getHeaderField(3)
        verify(exactly = 1) { mockConnection.getHeaderField(3) }
    }

    @Test
    fun testGetHeaderFieldString() {
        connectionDelegate.getHeaderField("foo")
        verify(exactly = 1) { mockConnection.getHeaderField("foo") }
    }

    @Test
    fun testGetHeaderFieldDate() {
        connectionDelegate.getHeaderFieldDate("date", 2012L)
        verify(exactly = 1) { mockConnection.getHeaderFieldDate("date", 2012L) }
    }

    @Test
    fun testGetHeaderFieldIntWithDefault() {
        connectionDelegate.getHeaderFieldInt("intVal", 5)
        verify(exactly = 1) { mockConnection.getHeaderFieldInt("intVal", 5) }
    }

    @Test
    fun testGetHeaderFieldKey() {
        connectionDelegate.getHeaderFieldKey(3)
        verify(exactly = 1) { mockConnection.getHeaderFieldKey(3) }
    }

    @Test
    fun testGetHeaderFieldLong() {
        connectionDelegate.getHeaderFieldLong("longVal", 8L)
        verify(exactly = 1) { mockConnection.getHeaderFieldLong("longVal", 8L) }
    }

    @Test
    fun testGetHeaderFields() {
        connectionDelegate.headerFields
        verify(exactly = 0) { mockConnection.headerFields }
    }

    @Test
    fun testGetIfModifiedSince() {
        connectionDelegate.ifModifiedSince
        verify(exactly = 1) { mockConnection.ifModifiedSince }
    }

    @Test
    fun testSetIfModifiedSince() {
        connectionDelegate.ifModifiedSince = 111111L
        verify(exactly = 1) { mockConnection.ifModifiedSince = 111111L }
    }

    @Test
    fun testGetInputStream() {
        connectionDelegate.inputStream
        verify(exactly = 1) { mockConnection.inputStream }
    }

    @Test
    fun testGetInstanceFollowRedirects() {
        connectionDelegate.instanceFollowRedirects
        verify(exactly = 1) { mockConnection.instanceFollowRedirects }
    }

    @Test
    fun testSetInstanceFollowRedirects() {
        connectionDelegate.instanceFollowRedirects = false
        verify(exactly = 1) { mockConnection.instanceFollowRedirects = false }
    }

    @Test
    fun testGetLastModified() {
        connectionDelegate.lastModified
        verify(exactly = 1) { mockConnection.lastModified }
    }

    @Test
    fun testGetOutputStream() {
        connectionDelegate.outputStream
        verify(exactly = 1) { mockConnection.outputStream }
    }

    @Test
    fun testGetPermission() {
        connectionDelegate.permission
        verify(exactly = 1) { mockConnection.permission }
    }

    @Test
    fun testGetReadTimeout() {
        connectionDelegate.readTimeout
        verify(exactly = 1) { mockConnection.readTimeout }
    }

    @Test
    fun testSetReadTimeout() {
        connectionDelegate.readTimeout = 10
        verify(exactly = 1) { mockConnection.readTimeout = 10 }
    }

    @Test
    fun testGetRequestMethod() {
        connectionDelegate.requestMethod
        verify(exactly = 1) { mockConnection.requestMethod }
    }

    @Test
    fun testSetRequestMethod() {
        connectionDelegate.requestMethod = "GET"
        verify(exactly = 1) { mockConnection.requestMethod = "GET" }
    }

    @Test
    fun testGetRequestProperties() {
        connectionDelegate.requestProperties
        verify(exactly = 1) { mockConnection.requestProperties }
    }

    @Test
    fun testGetRequestProperty() {
        connectionDelegate.getRequestProperty("content-encoding")
        verify(exactly = 1) { mockConnection.getRequestProperty("content-encoding") }
    }

    @Test
    fun testGetResponseCode() {
        connectionDelegate.responseCode
        verify(exactly = 0) { mockConnection.responseCode }
    }

    @Test
    fun testGetResponseMessage() {
        connectionDelegate.responseMessage
        verify(exactly = 1) { mockConnection.responseMessage }
    }

    @Test
    fun testGetURL() {
        connectionDelegate.url
        verify(exactly = 1) { mockConnection.url }
    }

    @Test
    fun testGetUseCaches() {
        connectionDelegate.useCaches
        verify(exactly = 1) { mockConnection.useCaches }
    }

    @Test
    fun testSetUseCaches() {
        connectionDelegate.useCaches = true
        verify(exactly = 1) { mockConnection.useCaches = true }
    }

    @Test
    fun testSetChunkedStreamingMode() {
        connectionDelegate.setChunkedStreamingMode(100)
        verify(exactly = 1) { mockConnection.setChunkedStreamingMode(100) }
    }

    @Test
    fun testSetFixedLengthStreamingModeInt() {
        connectionDelegate.setFixedLengthStreamingMode(1)
        verify(exactly = 1) { mockConnection.setFixedLengthStreamingMode(1) }
    }

    @Test
    fun testTestSetFixedLengthStreamingModeLong() {
        connectionDelegate.setFixedLengthStreamingMode(10L)
        verify(exactly = 1) { mockConnection.setFixedLengthStreamingMode(10L) }
    }

    @Test
    fun testSetRequestProperty() {
        connectionDelegate.setRequestProperty("key", "val")
        verify(exactly = 1) { mockConnection.setRequestProperty("key", "val") }
    }

    @Test
    fun testToString() {
        connectionDelegate.toString()
        verify(exactly = 1) { mockConnection.toString() }
    }

    @Test
    fun testUsingProxy() {
        connectionDelegate.usingProxy()
        verify(exactly = 1) { mockConnection.usingProxy() }
    }

    @Test
    fun getCipherSuite() {
        connectionDelegate.cipherSuite
        verify(exactly = 1) { mockConnection.cipherSuite }
    }

    @Test
    fun getLocalCertificates() {
        connectionDelegate.localCertificates
        verify(exactly = 1) { mockConnection.localCertificates }
    }

    @Test
    fun getServerCertificates() {
        connectionDelegate.serverCertificates
        verify(exactly = 1) { mockConnection.serverCertificates }
    }

    @Test
    fun getSSLSocketFactory() {
        connectionDelegate.sslSocketFactory
        verify(exactly = 1) { mockConnection.sslSocketFactory }
    }

    @Test
    fun setSSLSocketFactory() {
        val mockSslSocketFactory: SSLSocketFactory = mockk(relaxed = true)
        connectionDelegate.setSslSocketFactory(mockSslSocketFactory)
        verify(exactly = 1) { mockConnection.sslSocketFactory = mockSslSocketFactory }
    }

    @Test
    fun getHostnameVerifier() {
        connectionDelegate.hostnameVerifier
        verify(exactly = 1) { mockConnection.hostnameVerifier }
    }

    @Test
    fun setHostnameVerifier() {
        val mockHostnameVerifier: HostnameVerifier = mockk(relaxed = true)
        connectionDelegate.setHostnameVerifier(mockHostnameVerifier)
        verify(exactly = 1) { mockConnection.hostnameVerifier = mockHostnameVerifier }
    }

    @Test
    fun getLocalPrincipal() {
        connectionDelegate.localPrincipal
        verify(exactly = 1) { mockConnection.localPrincipal }
    }

    @Test
    fun getPeerPrincipal() {
        connectionDelegate.peerPrincipal
        verify(exactly = 1) { mockConnection.peerPrincipal }
    }
}
