package io.embrace.android.embracesdk.internal.network.http

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

internal class EmbraceHttpsUrlConnectionImplTest {
    private lateinit var mockDelegate: EmbraceUrlConnectionDelegate<HttpsURLConnection>
    private lateinit var embraceHttpsUrlConnectionImpl: EmbraceHttpsUrlConnectionImpl<HttpsURLConnection>

    @Before
    fun setup() {
        mockDelegate = mockk(relaxed = true)
        embraceHttpsUrlConnectionImpl = EmbraceHttpsUrlConnectionImpl(mockk(relaxed = true), mockDelegate)
    }

    @Test
    fun testAddRequestProperty() {
        embraceHttpsUrlConnectionImpl.addRequestProperty("test", "testValue")
        verify(exactly = 1) { mockDelegate.addRequestProperty("test", "testValue") }
    }

    @Test
    fun testConnect() {
        embraceHttpsUrlConnectionImpl.connect()
        verify(exactly = 1) { mockDelegate.connect() }
    }

    @Test
    fun testDisconnect() {
        embraceHttpsUrlConnectionImpl.disconnect()
        verify(exactly = 1) { mockDelegate.disconnect() }
    }

    @Test
    fun testGetAllowUserInteraction() {
        embraceHttpsUrlConnectionImpl.allowUserInteraction
        verify(exactly = 1) { mockDelegate.allowUserInteraction }
    }

    @Test
    fun testSetAllowUserInteraction() {
        embraceHttpsUrlConnectionImpl.allowUserInteraction = true
        verify(exactly = 1) { mockDelegate.allowUserInteraction = true }
    }

    @Test
    fun testGetConnectTimeout() {
        embraceHttpsUrlConnectionImpl.connectTimeout
        verify(exactly = 1) { mockDelegate.connectTimeout }
    }

    @Test
    fun testSetConnectTimeout() {
        embraceHttpsUrlConnectionImpl.connectTimeout = 5
        verify(exactly = 1) { mockDelegate.connectTimeout = 5 }
    }

    @Test
    fun testGetContent() {
        embraceHttpsUrlConnectionImpl.content
        verify(exactly = 1) { mockDelegate.content }
    }

    @Test
    fun testGetContentWithClasses() {
        val array = arrayOf(Object::class.java)
        embraceHttpsUrlConnectionImpl.getContent(array)
        verify(exactly = 1) { mockDelegate.getContent(array) }
    }

    @Test
    fun testGetContentEncoding() {
        embraceHttpsUrlConnectionImpl.contentEncoding
        verify(exactly = 1) { mockDelegate.contentEncoding }
    }

    @Test
    fun testGetContentLength() {
        embraceHttpsUrlConnectionImpl.contentLength
        verify(exactly = 1) { mockDelegate.contentLength }
    }

    @Test
    fun testGetContentLengthLong() {
        embraceHttpsUrlConnectionImpl.contentLengthLong
        verify(exactly = 1) { mockDelegate.contentLengthLong }
    }

    @Test
    fun testGetContentType() {
        embraceHttpsUrlConnectionImpl.contentType
        verify(exactly = 1) { mockDelegate.contentType }
    }

    @Test
    fun testGetDate() {
        embraceHttpsUrlConnectionImpl.date
        verify(exactly = 1) { mockDelegate.date }
    }

    @Test
    fun testGetDefaultUseCaches() {
        embraceHttpsUrlConnectionImpl.defaultUseCaches
        verify(exactly = 1) { mockDelegate.defaultUseCaches }
    }

    @Test
    fun testSetDefaultUseCaches() {
        embraceHttpsUrlConnectionImpl.defaultUseCaches = false
        verify(exactly = 1) { mockDelegate.defaultUseCaches = false }
    }

    @Test
    fun testGetDoInput() {
        embraceHttpsUrlConnectionImpl.doInput
        verify(exactly = 1) { mockDelegate.doInput }
    }

    @Test
    fun testSetDoInput() {
        embraceHttpsUrlConnectionImpl.doInput = true
        verify(exactly = 1) { mockDelegate.doInput = true }
    }

    @Test
    fun testGetDoOutput() {
        embraceHttpsUrlConnectionImpl.doOutput
        verify(exactly = 1) { mockDelegate.doOutput }
    }

    @Test
    fun testSetDoOutput() {
        embraceHttpsUrlConnectionImpl.doOutput = true
        verify(exactly = 1) { mockDelegate.doOutput = true }
    }

    @Test
    fun testGetErrorStream() {
        embraceHttpsUrlConnectionImpl.errorStream
        verify(exactly = 1) { mockDelegate.errorStream }
    }

    @Test
    fun testGetHeaderFieldInt() {
        embraceHttpsUrlConnectionImpl.getHeaderField(3)
        verify(exactly = 1) { mockDelegate.getHeaderField(3) }
    }

    @Test
    fun testGetHeaderFieldString() {
        embraceHttpsUrlConnectionImpl.getHeaderField("foo")
        verify(exactly = 1) { mockDelegate.getHeaderField("foo") }
    }

    @Test
    fun testGetHeaderFieldDate() {
        embraceHttpsUrlConnectionImpl.getHeaderFieldDate("date", 2012L)
        verify(exactly = 1) { mockDelegate.getHeaderFieldDate("date", 2012L) }
    }

    @Test
    fun testGetHeaderFieldIntWithDefault() {
        embraceHttpsUrlConnectionImpl.getHeaderFieldInt("intVal", 5)
        verify(exactly = 1) { mockDelegate.getHeaderFieldInt("intVal", 5) }
    }

    @Test
    fun testGetHeaderFieldKey() {
        embraceHttpsUrlConnectionImpl.getHeaderFieldKey(3)
        verify(exactly = 1) { mockDelegate.getHeaderFieldKey(3) }
    }

    @Test
    fun testGetHeaderFieldLong() {
        embraceHttpsUrlConnectionImpl.getHeaderFieldLong("longVal", 8L)
        verify(exactly = 1) { mockDelegate.getHeaderFieldLong("longVal", 8L) }
    }

    @Test
    fun testGetHeaderFields() {
        embraceHttpsUrlConnectionImpl.headerFields
        verify(exactly = 1) { mockDelegate.headerFields }
    }

    @Test
    fun testGetIfModifiedSince() {
        embraceHttpsUrlConnectionImpl.ifModifiedSince
        verify(exactly = 1) { mockDelegate.ifModifiedSince }
    }

    @Test
    fun testSetIfModifiedSince() {
        embraceHttpsUrlConnectionImpl.ifModifiedSince = 111111L
        verify(exactly = 1) { mockDelegate.ifModifiedSince = 111111L }
    }

    @Test
    fun testGetInputStream() {
        embraceHttpsUrlConnectionImpl.inputStream
        verify(exactly = 1) { mockDelegate.inputStream }
    }

    @Test
    fun testGetInstanceFollowRedirects() {
        embraceHttpsUrlConnectionImpl.instanceFollowRedirects
        verify(exactly = 1) { mockDelegate.instanceFollowRedirects }
    }

    @Test
    fun testSetInstanceFollowRedirects() {
        embraceHttpsUrlConnectionImpl.instanceFollowRedirects = false
        verify(exactly = 1) { mockDelegate.instanceFollowRedirects = false }
    }

    @Test
    fun testGetLastModified() {
        embraceHttpsUrlConnectionImpl.lastModified
        verify(exactly = 1) { mockDelegate.lastModified }
    }

    @Test
    fun testGetOutputStream() {
        embraceHttpsUrlConnectionImpl.outputStream
        verify(exactly = 1) { mockDelegate.outputStream }
    }

    @Test
    fun testGetPermission() {
        embraceHttpsUrlConnectionImpl.permission
        verify(exactly = 1) { mockDelegate.permission }
    }

    @Test
    fun testGetReadTimeout() {
        embraceHttpsUrlConnectionImpl.readTimeout
        verify(exactly = 1) { mockDelegate.readTimeout }
    }

    @Test
    fun testSetReadTimeout() {
        embraceHttpsUrlConnectionImpl.readTimeout = 10
        verify(exactly = 1) { mockDelegate.readTimeout = 10 }
    }

    @Test
    fun testGetRequestMethod() {
        embraceHttpsUrlConnectionImpl.requestMethod
        verify(exactly = 1) { mockDelegate.requestMethod }
    }

    @Test
    fun testSetRequestMethod() {
        embraceHttpsUrlConnectionImpl.requestMethod = "GET"
        verify(exactly = 1) { mockDelegate.requestMethod = "GET" }
    }

    @Test
    fun testGetRequestProperties() {
        embraceHttpsUrlConnectionImpl.requestProperties
        verify(exactly = 1) { mockDelegate.requestProperties }
    }

    @Test
    fun testGetRequestProperty() {
        embraceHttpsUrlConnectionImpl.getRequestProperty("content-encoding")
        verify(exactly = 1) { mockDelegate.getRequestProperty("content-encoding") }
    }

    @Test
    fun testGetResponseCode() {
        embraceHttpsUrlConnectionImpl.responseCode
        verify(exactly = 1) { mockDelegate.responseCode }
    }

    @Test
    fun testGetResponseMessage() {
        embraceHttpsUrlConnectionImpl.responseMessage
        verify(exactly = 1) { mockDelegate.responseMessage }
    }

    @Test
    fun testGetURL() {
        embraceHttpsUrlConnectionImpl.url
        verify(exactly = 1) { mockDelegate.url }
    }

    @Test
    fun testGetUseCaches() {
        embraceHttpsUrlConnectionImpl.useCaches
        verify(exactly = 1) { mockDelegate.useCaches }
    }

    @Test
    fun testSetUseCaches() {
        embraceHttpsUrlConnectionImpl.useCaches = true
        verify(exactly = 1) { mockDelegate.useCaches = true }
    }

    @Test
    fun testSetChunkedStreamingMode() {
        embraceHttpsUrlConnectionImpl.setChunkedStreamingMode(100)
        verify(exactly = 1) { mockDelegate.setChunkedStreamingMode(100) }
    }

    @Test
    fun testSetFixedLengthStreamingModeInt() {
        embraceHttpsUrlConnectionImpl.setFixedLengthStreamingMode(1)
        verify(exactly = 1) { mockDelegate.setFixedLengthStreamingMode(1) }
    }

    @Test
    fun testTestSetFixedLengthStreamingModeLong() {
        embraceHttpsUrlConnectionImpl.setFixedLengthStreamingMode(10L)
        verify(exactly = 1) { mockDelegate.setFixedLengthStreamingMode(10L) }
    }

    @Test
    fun testSetRequestProperty() {
        embraceHttpsUrlConnectionImpl.setRequestProperty("key", "val")
        verify(exactly = 1) { mockDelegate.setRequestProperty("key", "val") }
    }

    @Test
    fun testToString() {
        embraceHttpsUrlConnectionImpl.toString()
        verify(exactly = 1) { mockDelegate.toString() }
    }

    @Test
    fun testUsingProxy() {
        embraceHttpsUrlConnectionImpl.usingProxy()
        verify(exactly = 1) { mockDelegate.usingProxy() }
    }

    @Test
    fun getCipherSuite() {
        embraceHttpsUrlConnectionImpl.cipherSuite
        verify(exactly = 1) { mockDelegate.cipherSuite }
    }

    @Test
    fun getLocalCertificates() {
        embraceHttpsUrlConnectionImpl.localCertificates
        verify(exactly = 1) { mockDelegate.localCertificates }
    }

    @Test
    fun getServerCertificates() {
        embraceHttpsUrlConnectionImpl.serverCertificates
        verify(exactly = 1) { mockDelegate.serverCertificates }
    }

    @Test
    fun getSSLSocketFactory() {
        embraceHttpsUrlConnectionImpl.sslSocketFactory
        verify(exactly = 1) { mockDelegate.sslSocketFactory }
    }

    @Test
    fun setSSLSocketFactory() {
        val mockSslSocketFactory: SSLSocketFactory = mockk(relaxed = true)
        embraceHttpsUrlConnectionImpl.sslSocketFactory = mockSslSocketFactory
        verify(exactly = 1) { mockDelegate.setSslSocketFactory(mockSslSocketFactory) }
    }

    @Test
    fun getHostnameVerifier() {
        embraceHttpsUrlConnectionImpl.hostnameVerifier
        verify(exactly = 1) { mockDelegate.hostnameVerifier }
    }

    @Test
    fun setHostnameVerifier() {
        val mockHostnameVerifier: HostnameVerifier = mockk(relaxed = true)
        embraceHttpsUrlConnectionImpl.hostnameVerifier = mockHostnameVerifier
        verify(exactly = 1) { mockDelegate.setHostnameVerifier(mockHostnameVerifier) }
    }

    @Test
    fun getLocalPrincipal() {
        embraceHttpsUrlConnectionImpl.localPrincipal
        verify(exactly = 1) { mockDelegate.localPrincipal }
    }

    @Test
    fun getPeerPrincipal() {
        embraceHttpsUrlConnectionImpl.peerPrincipal
        verify(exactly = 1) { mockDelegate.peerPrincipal }
    }
}
