package io.embrace.android.embracesdk.instrumentation.huclite

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.Permission
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

@RunWith(AndroidJUnit4::class)
internal class InstrumentedHttpsURLConnectionTest {
    private lateinit var harness: HucTestHarness

    @Before
    fun setup() {
        harness = HucTestHarness(sdkEnabled = true)
    }

    @Test
    fun `standard GET request lifecycle records a request`() = harness.runTest {
        with(instrumentedConnection) {
            connect()
            responseCode
            disconnect()
        }

        assertSingleSuccessfulRequest(expectedEndTime = getCurrentTimeMs())
    }

    @Test
    fun `exception during request lifecycle records a error`() = harness.runTest {
        var startTime = 0L
        every { mockWrappedConnection.responseCode } throws FakeIOException()
        with(instrumentedConnection) {
            moveTimeForward()
            startTime = getCurrentTimeMs()
            connect()
            moveTimeForward()
            assertThrows(FakeIOException::class.java) {
                moveTimeForward()
                responseCode
            }
            disconnect()
        }

        assertSingleClientError(
            expectedStartTime = startTime,
            expectedEndTime = getCurrentTimeMs()
        )
    }

    @Test
    fun `standard POST request lifecycle records a request`() = harness.runTest {
        val mockInputStream = mockk<InputStream>()
        every { mockInputStream.read() } returns 1
        every { mockWrappedConnection.inputStream } returns mockInputStream
        every { mockWrappedConnection.requestMethod } returns "POST"
        var startTime = 0L
        var endTime = 0L
        with(instrumentedConnection) {
            moveTimeForward()
            requestMethod = "POST"
            moveTimeForward()
            startTime = getCurrentTimeMs()
            connect()
            moveTimeForward()
            outputStream.write("derp".toByteArray())
            moveTimeForward()
            inputStream.read()
            endTime = getCurrentTimeMs()
            moveTimeForward()
            responseCode
        }

        verify(exactly = 1) { mockWrappedConnection.requestMethod = "POST" }

        assertSingleSuccessfulRequest(
            expectedMethod = "POST",
            expectedStartTime = startTime,
            expectedEndTime = endTime
        )
    }

    @Test
    fun `connect delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.connect()
        verify(exactly = 1) { mockWrappedConnection.connect() }
        assertNoRequestRecorded()
    }

    @Test
    fun `connect records error when the wrapped call throws an exception`() = harness.runTest {
        var connectionTime = 0L
        every { mockWrappedConnection.connect() } answers {
            connectionTime = moveTimeForward()
            throw FakeIOException()
        }

        val startConnectionTime = moveTimeForward()
        assertThrows(IOException::class.java) {
            instrumentedConnection.connect()
        }
        assertSingleClientError(startConnectionTime, connectionTime)
    }

    @Test
    fun `getOutputStream delegates to wrapped connection`() = harness.runTest {
        val mockOutputStream = mockk<OutputStream>()
        every { mockWrappedConnection.outputStream } returns mockOutputStream

        val result = instrumentedConnection.outputStream

        assertEquals(mockOutputStream, result)
        verify(exactly = 1) { mockWrappedConnection.outputStream }
    }

    @Test
    fun `getOutputStream records error when wrapped connection throws an exception`() = harness.runTest {
        every { mockWrappedConnection.outputStream } throws FakeIOException()
        assertThrows(IOException::class.java) {
            instrumentedConnection.outputStream
        }
        assertSingleClientError()
    }

    @Test
    fun `getResponseCode delegates to wrapped connection`() = harness.runTest {
        val result = instrumentedConnection.responseCode
        assertEquals(200, result)

        // First call is to invoked the wrapped method - second is to cache the response for access later by instrumentation code
        verify(exactly = 2) { mockWrappedConnection.responseCode }

        // Subsequent call only a pass-through as the caching is not necessary
        instrumentedConnection.responseCode
        verify(exactly = 3) { mockWrappedConnection.responseCode }
    }

    @Test
    fun `getResponseCode only records network request the first time even if it is accessed multiple times`() = harness.runTest {
        val startTime = moveTimeForward()
        instrumentedConnection.responseCode
        val endTime = getCurrentTimeMs()
        moveTimeForward()
        instrumentedConnection.responseCode
        assertSingleSuccessfulRequest(expectedStartTime = startTime, expectedEndTime = endTime)
    }

    @Test
    fun `getResponseCode records error if the underlying call throws an exception`() = harness.runTest {
        every { mockWrappedConnection.responseCode } throws FakeIOException()
        assertThrows(IOException::class.java) {
            instrumentedConnection.responseCode
        }
        assertSingleClientError()
    }

    @Test
    fun `getResponseCode logs a request with the path override URL is provided`() = harness.runTest {
        every { mockWrappedConnection.getRequestProperty("x-emb-path") } returns "/override/path"
        val startTime = getCurrentTimeMs()
        instrumentedConnection.responseCode

        assertSingleSuccessfulRequest(
            expectedStartTime = startTime,
            expectedEndTime = getCurrentTimeMs(),
            expectedUrl = "https://fakeurl.pizza/override/path?doStuff=true"
        )
    }

    @Test
    fun `getResponseCode logs a request with the original URL if overridden path is malformed`() = harness.runTest {
        every { mockWrappedConnection.getRequestProperty("x-emb-path") } returns "\\\\\\     /override/path"
        val startTime = getCurrentTimeMs()
        instrumentedConnection.responseCode

        assertSingleSuccessfulRequest(
            expectedStartTime = startTime,
            expectedEndTime = getCurrentTimeMs()
        )
    }

    @Test
    fun `getResponseCode logs POST requests with non-200 responses`() = harness.runTest {
        every { mockWrappedConnection.responseCode } returns 503
        every { mockWrappedConnection.requestMethod } returns "POST"
        instrumentedConnection.responseCode

        assertSingleSuccessfulRequest(
            expectedStartTime = FAKE_TIME_MS,
            expectedEndTime = FAKE_TIME_MS,
            expectedResponseCode = 503,
            expectedMethod = "POST"
        )
    }

    @Test
    fun `getResponseMessage delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.responseMessage } returns FAKE_VALUE
        val result = instrumentedConnection.responseMessage
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.responseMessage }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderField with int delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderField(0) } returns FAKE_VALUE
        val result = instrumentedConnection.getHeaderField(0)
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderField(0) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderFieldKey delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderFieldKey(0) } returns FAKE_VALUE
        val result = instrumentedConnection.getHeaderFieldKey(0)
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderFieldKey(0) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getErrorStream delegates to wrapped connection and records telemetry`() = harness.runTest {
        val mockErrorStream = mockk<InputStream>()
        every { mockWrappedConnection.errorStream } returns mockErrorStream
        val result = instrumentedConnection.errorStream
        assertEquals(mockErrorStream, result)
        verify(exactly = 1) { mockWrappedConnection.errorStream }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderField with string delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderField(FAKE_FIELD_NAME) } returns FAKE_VALUE
        val result = instrumentedConnection.getHeaderField(FAKE_FIELD_NAME)
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderField(FAKE_FIELD_NAME) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderFields delegates to wrapped connection and records telemetry`() = harness.runTest {
        val mockHeaders = mutableMapOf(FAKE_VALUE to mutableListOf(FAKE_VALUE))
        every { mockWrappedConnection.headerFields } returns mockHeaders
        val result = instrumentedConnection.headerFields
        assertEquals(mockHeaders, result)
        verify(exactly = 1) { mockWrappedConnection.headerFields }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getInputStream delegates to wrapped connection and records telemetry`() = harness.runTest {
        val mockInputStream = mockk<InputStream>()
        every { mockWrappedConnection.inputStream } returns mockInputStream
        val result = instrumentedConnection.inputStream
        assertEquals(mockInputStream, result)
        verify(exactly = 1) { mockWrappedConnection.inputStream }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getContent delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.content } returns FAKE_VALUE
        val result = instrumentedConnection.content
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.content }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getContent with classes delegates to wrapped connection and records telemetry`() = harness.runTest {
        val classes = arrayOf(String::class.java)
        every { mockWrappedConnection.getContent(classes) } returns FAKE_VALUE
        val result = instrumentedConnection.getContent(classes)
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.getContent(classes) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderFieldInt delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderFieldInt(FAKE_FIELD_NAME, 0) } returns 1024
        val result = instrumentedConnection.getHeaderFieldInt(FAKE_FIELD_NAME, 0)
        assertEquals(1024, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderFieldInt(FAKE_FIELD_NAME, 0) }
        assertSingleSuccessfulRequest()
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `getHeaderFieldLong delegates to wrapped connection for Android 24+ and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderFieldLong(FAKE_FIELD_NAME, 0L) } returns 1024L
        val result = instrumentedConnection.getHeaderFieldLong(FAKE_FIELD_NAME, 0L)
        assertEquals(1024L, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderFieldLong(FAKE_FIELD_NAME, 0L) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderFieldLong delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderFieldInt(FAKE_FIELD_NAME, 0) } returns 1024
        val result = instrumentedConnection.getHeaderFieldLong(FAKE_FIELD_NAME, 0L)
        assertEquals(1024L, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderFieldInt(FAKE_FIELD_NAME, 0) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getHeaderFieldDate delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.getHeaderFieldDate(FAKE_FIELD_NAME, 0L) } returns FAKE_TIME_MS
        val result = instrumentedConnection.getHeaderFieldDate(FAKE_FIELD_NAME, 0L)
        assertEquals(FAKE_TIME_MS, result)
        verify(exactly = 1) { mockWrappedConnection.getHeaderFieldDate(FAKE_FIELD_NAME, 0L) }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getContentEncoding delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.contentEncoding } returns "gzip"
        val result = instrumentedConnection.contentEncoding
        assertEquals("gzip", result)
        verify(exactly = 1) { mockWrappedConnection.contentEncoding }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getContentLength delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.contentLength } returns 512
        val result = instrumentedConnection.contentLength
        assertEquals(512, result)
        verify(exactly = 1) { mockWrappedConnection.contentLength }
        assertSingleSuccessfulRequest()
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `getContentLengthLong delegates to wrapped connection on Android 24+ and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.contentLengthLong } returns 512L
        val result = instrumentedConnection.contentLengthLong
        assertEquals(512L, result)
        verify(exactly = 1) { mockWrappedConnection.contentLengthLong }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getContentLengthLong delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.contentLength } returns 512
        val result = instrumentedConnection.contentLengthLong
        assertEquals(512L, result)
        verify(exactly = 1) { mockWrappedConnection.contentLength }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getContentType delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.contentType } returns FAKE_VALUE
        val result = instrumentedConnection.contentType
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.contentType }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getDate delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.date } returns FAKE_TIME_MS
        val result = instrumentedConnection.date
        assertEquals(FAKE_TIME_MS, result)
        verify(exactly = 1) { mockWrappedConnection.date }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getExpiration delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.expiration } returns FAKE_TIME_MS
        val result = instrumentedConnection.expiration
        assertEquals(FAKE_TIME_MS, result)
        verify(exactly = 1) { mockWrappedConnection.expiration }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `getLastModified delegates to wrapped connection and records telemetry`() = harness.runTest {
        every { mockWrappedConnection.lastModified } returns FAKE_TIME_MS
        val result = instrumentedConnection.lastModified
        assertEquals(FAKE_TIME_MS, result)
        verify(exactly = 1) { mockWrappedConnection.lastModified }
        assertSingleSuccessfulRequest()
    }

    @Test
    fun `exception during instrumentation should not throw`() = harness.runTest {
        every { mockWrappedConnection.url } throws FakeIOException()
        instrumentedConnection.responseCode
        assertNoRequestRecorded()
        assertTrue(harness.fakeInternalInterface.internalErrors.single() is FakeIOException)
    }

    @Test
    fun `disconnect delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.disconnect()
        verify(exactly = 1) { mockWrappedConnection.disconnect() }
    }

    @Test
    fun `getCipherSuite delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.cipherSuite } returns FAKE_VALUE
        val result = instrumentedConnection.cipherSuite
        assertEquals(FAKE_VALUE, result)
        verify(exactly = 1) { mockWrappedConnection.cipherSuite }
        assertNoRequestRecorded()
    }

    @Test
    fun `getLocalCertificates delegates to wrapped connection`() = harness.runTest {
        val mockCerts = arrayOf<Certificate>(mockk())
        every { mockWrappedConnection.localCertificates } returns mockCerts

        val result = instrumentedConnection.localCertificates

        assertSame(mockCerts, result)
        verify(exactly = 1) { mockWrappedConnection.localCertificates }
        assertNoRequestRecorded()
    }

    @Test
    fun `getServerCertificates delegates to wrapped connection`() = harness.runTest {
        val mockCerts = arrayOf<Certificate>(mockk())
        every { mockWrappedConnection.serverCertificates } returns mockCerts

        val result = instrumentedConnection.serverCertificates

        assertSame(mockCerts, result)
        verify(exactly = 1) { mockWrappedConnection.serverCertificates }
        assertNoRequestRecorded()
    }

    @Test
    fun `getHostnameVerifier delegates to wrapped connection`() = harness.runTest {
        val mockVerifier = mockk<HostnameVerifier>()
        every { mockWrappedConnection.hostnameVerifier } returns mockVerifier

        val result = instrumentedConnection.hostnameVerifier

        assertEquals(mockVerifier, result)
        verify(exactly = 1) { mockWrappedConnection.hostnameVerifier }
        assertNoRequestRecorded()
    }

    @Test
    fun `setHostnameVerifier delegates to wrapped connection`() = harness.runTest {
        val mockVerifier = mockk<HostnameVerifier>()

        instrumentedConnection.hostnameVerifier = mockVerifier

        verify(exactly = 1) { mockWrappedConnection.hostnameVerifier = mockVerifier }
        assertNoRequestRecorded()
    }

    @Test
    fun `getSSLSocketFactory delegates to wrapped connection`() = harness.runTest {
        val mockFactory = mockk<SSLSocketFactory>()
        every { mockWrappedConnection.sslSocketFactory } returns mockFactory

        val result = instrumentedConnection.sslSocketFactory

        assertEquals(mockFactory, result)
        verify(exactly = 1) { mockWrappedConnection.sslSocketFactory }
        assertNoRequestRecorded()
    }

    @Test
    fun `setSSLSocketFactory delegates to wrapped connection`() = harness.runTest {
        val mockFactory = mockk<SSLSocketFactory>()

        instrumentedConnection.sslSocketFactory = mockFactory

        verify(exactly = 1) { mockWrappedConnection.sslSocketFactory = mockFactory }
        assertNoRequestRecorded()
    }

    @Test
    fun `getLocalPrincipal delegates to wrapped connection`() = harness.runTest {
        val mockPrincipal = mockk<Principal>()
        every { mockWrappedConnection.localPrincipal } returns mockPrincipal

        val result = instrumentedConnection.localPrincipal

        assertEquals(mockPrincipal, result)
        verify(exactly = 1) { mockWrappedConnection.localPrincipal }
        assertNoRequestRecorded()
    }

    @Test
    fun `getPeerPrincipal delegates to wrapped connection`() = harness.runTest {
        val mockPrincipal = mockk<Principal>()
        every { mockWrappedConnection.peerPrincipal } returns mockPrincipal

        val result = instrumentedConnection.peerPrincipal

        assertEquals(mockPrincipal, result)
        verify(exactly = 1) { mockWrappedConnection.peerPrincipal }
        assertNoRequestRecorded()
    }

    // Test HttpURLConnection methods

    @Test
    fun `usingProxy delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.usingProxy() } returns true

        val result = instrumentedConnection.usingProxy()

        assertTrue(result)
        verify(exactly = 1) { mockWrappedConnection.usingProxy() }
        assertNoRequestRecorded()
    }

    @Test
    fun `getRequestMethod delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.requestMethod } returns "POST"

        val result = instrumentedConnection.requestMethod

        assertEquals("POST", result)
        verify(atLeast = 1) { mockWrappedConnection.requestMethod }
        assertNoRequestRecorded()
    }

    @Test
    fun `setRequestMethod delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.requestMethod = "POST"
        verify(exactly = 1) { mockWrappedConnection.requestMethod = "POST" }
        assertNoRequestRecorded()
    }

    @Test
    fun `setFixedLengthStreamingMode with long delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.setFixedLengthStreamingMode(1024L)
        verify(exactly = 1) { mockWrappedConnection.setFixedLengthStreamingMode(1024L) }
        assertNoRequestRecorded()
    }

    @Test
    fun `setFixedLengthStreamingMode with int delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.setFixedLengthStreamingMode(1024)
        verify(exactly = 1) { mockWrappedConnection.setFixedLengthStreamingMode(1024) }
        assertNoRequestRecorded()
    }

    @Test
    fun `setChunkedStreamingMode delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.setChunkedStreamingMode(256)
        verify(exactly = 1) { mockWrappedConnection.setChunkedStreamingMode(256) }
        assertNoRequestRecorded()
    }

    @Test
    fun `getInstanceFollowRedirects delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.instanceFollowRedirects } returns true
        val result = instrumentedConnection.instanceFollowRedirects
        assertTrue(result)
        verify(exactly = 1) { mockWrappedConnection.instanceFollowRedirects }
        assertNoRequestRecorded()
    }

    @Test
    fun `setInstanceFollowRedirects delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.instanceFollowRedirects = false
        verify(exactly = 1) { mockWrappedConnection.instanceFollowRedirects = false }
        assertNoRequestRecorded()
    }

    @Test
    fun `getPermission delegates to wrapped connection`() = harness.runTest {
        val mockPermission = mockk<Permission>()
        every { mockWrappedConnection.permission } returns mockPermission
        val result = instrumentedConnection.permission
        assertEquals(mockPermission, result)
        verify(exactly = 1) { mockWrappedConnection.permission }
        assertNoRequestRecorded()
    }

    @Test
    fun `addRequestProperty delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.addRequestProperty(FAKE_FIELD_NAME, FAKE_VALUE)
        verify(exactly = 1) { mockWrappedConnection.addRequestProperty(FAKE_FIELD_NAME, FAKE_VALUE) }
        assertNoRequestRecorded()
    }

    @Test
    fun `getRequestProperty delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.getRequestProperty(FAKE_FIELD_NAME) } returns FAKE_VALUE
        val result = instrumentedConnection.getRequestProperty(FAKE_FIELD_NAME)
        assertEquals(FAKE_VALUE, result)
        verify(atLeast = 1) { mockWrappedConnection.getRequestProperty(FAKE_FIELD_NAME) }
        assertNoRequestRecorded()
    }

    @Test
    fun `setRequestProperty delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.setRequestProperty(FAKE_VALUE, FAKE_VALUE)
        verify(exactly = 1) { mockWrappedConnection.setRequestProperty(FAKE_VALUE, FAKE_VALUE) }
        assertNoRequestRecorded()
    }

    @Test
    fun `getRequestProperties delegates to wrapped connection`() = harness.runTest {
        val mockProps = mutableMapOf(FAKE_VALUE to mutableListOf(FAKE_VALUE))
        every { mockWrappedConnection.requestProperties } returns mockProps
        val result = instrumentedConnection.requestProperties
        assertEquals(mockProps, result)
        verify(exactly = 1) { mockWrappedConnection.requestProperties }
        assertNoRequestRecorded()
    }

    @Test
    fun `setDoInput delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.doInput = true
        verify(exactly = 1) { mockWrappedConnection.doInput = true }
        assertNoRequestRecorded()
    }

    @Test
    fun `getDoInput delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.doInput } returns true
        val result = instrumentedConnection.doInput
        assertTrue(result)
        verify(exactly = 1) { mockWrappedConnection.doInput }
        assertNoRequestRecorded()
    }

    @Test
    fun `setDoOutput delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.doOutput = true
        verify(exactly = 1) { mockWrappedConnection.doOutput = true }
        assertNoRequestRecorded()
    }

    @Test
    fun `getDoOutput delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.doOutput } returns false
        val result = instrumentedConnection.doOutput
        assertFalse(result)
        verify(exactly = 1) { mockWrappedConnection.doOutput }
        assertNoRequestRecorded()
    }

    @Test
    fun `setAllowUserInteraction delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.allowUserInteraction = true
        verify(exactly = 1) { mockWrappedConnection.allowUserInteraction = true }
        assertNoRequestRecorded()
    }

    @Test
    fun `getAllowUserInteraction delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.allowUserInteraction } returns false
        val result = instrumentedConnection.allowUserInteraction
        assertFalse(result)
        verify(exactly = 1) { mockWrappedConnection.allowUserInteraction }
        assertNoRequestRecorded()
    }

    @Test
    fun `setUseCaches delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.useCaches = true
        verify(exactly = 1) { mockWrappedConnection.useCaches = true }
        assertNoRequestRecorded()
    }

    @Test
    fun `getUseCaches delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.useCaches } returns true
        val result = instrumentedConnection.useCaches
        assertTrue(result)
        verify(exactly = 1) { mockWrappedConnection.useCaches }
        assertNoRequestRecorded()
    }

    @Test
    fun `setIfModifiedSince delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.ifModifiedSince = FAKE_TIME_MS
        verify(exactly = 1) { mockWrappedConnection.ifModifiedSince = FAKE_TIME_MS }
        assertNoRequestRecorded()
    }

    @Test
    fun `getIfModifiedSince delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.ifModifiedSince } returns FAKE_TIME_MS
        val result = instrumentedConnection.ifModifiedSince
        assertEquals(FAKE_TIME_MS, result)
        verify(exactly = 1) { mockWrappedConnection.ifModifiedSince }
        assertNoRequestRecorded()
    }

    @Test
    fun `getDefaultUseCaches delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.defaultUseCaches } returns true
        val result = instrumentedConnection.defaultUseCaches
        assertTrue(result)
        verify(exactly = 1) { mockWrappedConnection.defaultUseCaches }
        assertNoRequestRecorded()
    }

    @Test
    fun `setDefaultUseCaches delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.defaultUseCaches = false
        verify(exactly = 1) { mockWrappedConnection.defaultUseCaches = false }
        assertNoRequestRecorded()
    }

    @Test
    fun `setConnectTimeout delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.connectTimeout = 5000
        verify(exactly = 1) { mockWrappedConnection.connectTimeout = 5000 }
        assertNoRequestRecorded()
    }

    @Test
    fun `getConnectTimeout delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.connectTimeout } returns 5000
        val result = instrumentedConnection.connectTimeout
        assertEquals(5000, result)
        verify(exactly = 1) { mockWrappedConnection.connectTimeout }
        assertNoRequestRecorded()
    }

    @Test
    fun `setReadTimeout delegates to wrapped connection`() = harness.runTest {
        instrumentedConnection.readTimeout = 10000
        verify(exactly = 1) { mockWrappedConnection.readTimeout = 10000 }
        assertNoRequestRecorded()
    }

    @Test
    fun `getReadTimeout delegates to wrapped connection`() = harness.runTest {
        every { mockWrappedConnection.readTimeout } returns 10000
        val result = instrumentedConnection.readTimeout
        assertEquals(10000, result)
        verify(exactly = 1) { mockWrappedConnection.readTimeout }
        assertNoRequestRecorded()
    }

    @Test
    fun `getURL delegates to wrapped connection`() = harness.runTest {
        val result = instrumentedConnection.url
        assertEquals(testUrl, result)
        verify(atLeast = 1) { mockWrappedConnection.url }
        assertNoRequestRecorded()
    }
}
