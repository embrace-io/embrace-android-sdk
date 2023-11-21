package io.embrace.android.embracesdk.internal.network.http

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection

internal class EmbraceHttpUrlConnectionImplTest {

    private lateinit var mockDelegate: EmbraceUrlConnectionDelegate<HttpURLConnection>
    private lateinit var embraceHttpUrlConnectionImpl: EmbraceHttpUrlConnectionImpl<HttpURLConnection>

    @Before
    fun setup() {
        mockDelegate = mockk(relaxed = true)
        embraceHttpUrlConnectionImpl = EmbraceHttpUrlConnectionImpl(mockk(relaxed = true), mockDelegate)
    }

    @Test
    fun testAddRequestProperty() {
        embraceHttpUrlConnectionImpl.addRequestProperty("test", "testValue")
        verify(exactly = 1) { mockDelegate.addRequestProperty("test", "testValue") }
    }

    @Test
    fun testConnect() {
        embraceHttpUrlConnectionImpl.connect()
        verify(exactly = 1) { mockDelegate.connect() }
    }

    @Test
    fun testDisconnect() {
        embraceHttpUrlConnectionImpl.disconnect()
        verify(exactly = 1) { mockDelegate.disconnect() }
    }

    @Test
    fun testGetAllowUserInteraction() {
        embraceHttpUrlConnectionImpl.allowUserInteraction
        verify(exactly = 1) { mockDelegate.allowUserInteraction }
    }

    @Test
    fun testSetAllowUserInteraction() {
        embraceHttpUrlConnectionImpl.allowUserInteraction = true
        verify(exactly = 1) { mockDelegate.allowUserInteraction = true }
    }

    @Test
    fun testGetConnectTimeout() {
        embraceHttpUrlConnectionImpl.connectTimeout
        verify(exactly = 1) { mockDelegate.connectTimeout }
    }

    @Test
    fun testSetConnectTimeout() {
        embraceHttpUrlConnectionImpl.connectTimeout = 5
        verify(exactly = 1) { mockDelegate.connectTimeout = 5 }
    }

    @Test
    fun testGetContent() {
        embraceHttpUrlConnectionImpl.content
        verify(exactly = 1) { mockDelegate.content }
    }

    @Test
    fun testGetContentWithClasses() {
        val array = arrayOf(Object::class.java)
        embraceHttpUrlConnectionImpl.getContent(array)
        verify(exactly = 1) { mockDelegate.getContent(array) }
    }

    @Test
    fun testGetContentEncoding() {
        embraceHttpUrlConnectionImpl.contentEncoding
        verify(exactly = 1) { mockDelegate.contentEncoding }
    }

    @Test
    fun testGetContentLength() {
        embraceHttpUrlConnectionImpl.contentLength
        verify(exactly = 1) { mockDelegate.contentLength }
    }

    @Test
    fun testGetContentLengthLong() {
        embraceHttpUrlConnectionImpl.contentLengthLong
        verify(exactly = 1) { mockDelegate.contentLengthLong }
    }

    @Test
    fun testGetContentType() {
        embraceHttpUrlConnectionImpl.contentType
        verify(exactly = 1) { mockDelegate.contentType }
    }

    @Test
    fun testGetDate() {
        embraceHttpUrlConnectionImpl.date
        verify(exactly = 1) { mockDelegate.date }
    }

    @Test
    fun testGetDefaultUseCaches() {
        embraceHttpUrlConnectionImpl.defaultUseCaches
        verify(exactly = 1) { mockDelegate.defaultUseCaches }
    }

    @Test
    fun testSetDefaultUseCaches() {
        embraceHttpUrlConnectionImpl.defaultUseCaches = false
        verify(exactly = 1) { mockDelegate.defaultUseCaches = false }
    }

    @Test
    fun testGetDoInput() {
        embraceHttpUrlConnectionImpl.doInput
        verify(exactly = 1) { mockDelegate.doInput }
    }

    @Test
    fun testSetDoInput() {
        embraceHttpUrlConnectionImpl.doInput = true
        verify(exactly = 1) { mockDelegate.doInput = true }
    }

    @Test
    fun testGetDoOutput() {
        embraceHttpUrlConnectionImpl.doOutput
        verify(exactly = 1) { mockDelegate.doOutput }
    }

    @Test
    fun testSetDoOutput() {
        embraceHttpUrlConnectionImpl.doOutput = true
        verify(exactly = 1) { mockDelegate.doOutput = true }
    }

    @Test
    fun testGetErrorStream() {
        embraceHttpUrlConnectionImpl.errorStream
        verify(exactly = 1) { mockDelegate.errorStream }
    }

    @Test
    fun testGetHeaderFieldInt() {
        embraceHttpUrlConnectionImpl.getHeaderField(3)
        verify(exactly = 1) { mockDelegate.getHeaderField(3) }
    }

    @Test
    fun testGetHeaderFieldString() {
        embraceHttpUrlConnectionImpl.getHeaderField("foo")
        verify(exactly = 1) { mockDelegate.getHeaderField("foo") }
    }

    @Test
    fun testGetHeaderFieldDate() {
        embraceHttpUrlConnectionImpl.getHeaderFieldDate("date", 2012L)
        verify(exactly = 1) { mockDelegate.getHeaderFieldDate("date", 2012L) }
    }

    @Test
    fun testGetHeaderFieldIntWithDefault() {
        embraceHttpUrlConnectionImpl.getHeaderFieldInt("intVal", 5)
        verify(exactly = 1) { mockDelegate.getHeaderFieldInt("intVal", 5) }
    }

    @Test
    fun testGetHeaderFieldKey() {
        embraceHttpUrlConnectionImpl.getHeaderFieldKey(3)
        verify(exactly = 1) { mockDelegate.getHeaderFieldKey(3) }
    }

    @Test
    fun testGetHeaderFieldLong() {
        embraceHttpUrlConnectionImpl.getHeaderFieldLong("longVal", 8L)
        verify(exactly = 1) { mockDelegate.getHeaderFieldLong("longVal", 8L) }
    }

    @Test
    fun testGetHeaderFields() {
        embraceHttpUrlConnectionImpl.headerFields
        verify(exactly = 1) { mockDelegate.headerFields }
    }

    @Test
    fun testGetIfModifiedSince() {
        embraceHttpUrlConnectionImpl.ifModifiedSince
        verify(exactly = 1) { mockDelegate.ifModifiedSince }
    }

    @Test
    fun testSetIfModifiedSince() {
        embraceHttpUrlConnectionImpl.ifModifiedSince = 111111L
        verify(exactly = 1) { mockDelegate.ifModifiedSince = 111111L }
    }

    @Test
    fun testGetInputStream() {
        embraceHttpUrlConnectionImpl.inputStream
        verify(exactly = 1) { mockDelegate.inputStream }
    }

    @Test
    fun testGetInstanceFollowRedirects() {
        embraceHttpUrlConnectionImpl.instanceFollowRedirects
        verify(exactly = 1) { mockDelegate.instanceFollowRedirects }
    }

    @Test
    fun testSetInstanceFollowRedirects() {
        embraceHttpUrlConnectionImpl.instanceFollowRedirects = false
        verify(exactly = 1) { mockDelegate.instanceFollowRedirects = false }
    }

    @Test
    fun testGetLastModified() {
        embraceHttpUrlConnectionImpl.lastModified
        verify(exactly = 1) { mockDelegate.lastModified }
    }

    @Test
    fun testGetOutputStream() {
        embraceHttpUrlConnectionImpl.outputStream
        verify(exactly = 1) { mockDelegate.outputStream }
    }

    @Test
    fun testGetPermission() {
        embraceHttpUrlConnectionImpl.permission
        verify(exactly = 1) { mockDelegate.permission }
    }

    @Test
    fun testGetReadTimeout() {
        embraceHttpUrlConnectionImpl.readTimeout
        verify(exactly = 1) { mockDelegate.readTimeout }
    }

    @Test
    fun testSetReadTimeout() {
        embraceHttpUrlConnectionImpl.readTimeout = 10
        verify(exactly = 1) { mockDelegate.readTimeout = 10 }
    }

    @Test
    fun testGetRequestMethod() {
        embraceHttpUrlConnectionImpl.requestMethod
        verify(exactly = 1) { mockDelegate.requestMethod }
    }

    @Test
    fun testSetRequestMethod() {
        embraceHttpUrlConnectionImpl.requestMethod = "GET"
        verify(exactly = 1) { mockDelegate.requestMethod = "GET" }
    }

    @Test
    fun testGetRequestProperties() {
        embraceHttpUrlConnectionImpl.requestProperties
        verify(exactly = 1) { mockDelegate.requestProperties }
    }

    @Test
    fun testGetRequestProperty() {
        embraceHttpUrlConnectionImpl.getRequestProperty("content-encoding")
        verify(exactly = 1) { mockDelegate.getRequestProperty("content-encoding") }
    }

    @Test
    fun testGetResponseCode() {
        embraceHttpUrlConnectionImpl.responseCode
        verify(exactly = 1) { mockDelegate.responseCode }
    }

    @Test
    fun testGetResponseMessage() {
        embraceHttpUrlConnectionImpl.responseMessage
        verify(exactly = 1) { mockDelegate.responseMessage }
    }

    @Test
    fun testGetURL() {
        embraceHttpUrlConnectionImpl.url
        verify(exactly = 1) { mockDelegate.url }
    }

    @Test
    fun testGetUseCaches() {
        embraceHttpUrlConnectionImpl.useCaches
        verify(exactly = 1) { mockDelegate.useCaches }
    }

    @Test
    fun testSetUseCaches() {
        embraceHttpUrlConnectionImpl.useCaches = true
        verify(exactly = 1) { mockDelegate.useCaches = true }
    }

    @Test
    fun testSetChunkedStreamingMode() {
        embraceHttpUrlConnectionImpl.setChunkedStreamingMode(100)
        verify(exactly = 1) { mockDelegate.setChunkedStreamingMode(100) }
    }

    @Test
    fun testSetFixedLengthStreamingModeInt() {
        embraceHttpUrlConnectionImpl.setFixedLengthStreamingMode(1)
        verify(exactly = 1) { mockDelegate.setFixedLengthStreamingMode(1) }
    }

    @Test
    fun testTestSetFixedLengthStreamingModeLong() {
        embraceHttpUrlConnectionImpl.setFixedLengthStreamingMode(10L)
        verify(exactly = 1) { mockDelegate.setFixedLengthStreamingMode(10L) }
    }

    @Test
    fun testSetRequestProperty() {
        embraceHttpUrlConnectionImpl.setRequestProperty("key", "val")
        verify(exactly = 1) { mockDelegate.setRequestProperty("key", "val") }
    }

    @Test
    fun testToString() {
        embraceHttpUrlConnectionImpl.toString()
        verify(exactly = 1) { mockDelegate.toString() }
    }

    @Test
    fun testUsingProxy() {
        embraceHttpUrlConnectionImpl.usingProxy()
        verify(exactly = 1) { mockDelegate.usingProxy() }
    }
}
