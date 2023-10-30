package io.embrace.android.embracesdk

import android.net.Uri
import android.webkit.URLUtil
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.defaultImpl
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var impl: EmbraceInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var fakeClock: FakeClock
    private lateinit var initModule: InitModule

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        initModule = FakeInitModule(clock = fakeClock)
        impl = EmbraceInternalInterfaceImpl(embrace, initModule)
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = false
    }

    @Test
    fun testLogInfo() {
        impl.logInfo("", emptyMap())
        verify(exactly = 1) {
            embrace.logMessage(
                EmbraceEvent.Type.INFO_LOG,
                "",
                emptyMap(),
                null,
                null,
                LogExceptionType.NONE,
                null,
                null
            )
        }
    }

    @Test
    fun testLogWarning() {
        impl.logWarning("", emptyMap(), null)
        verify(exactly = 1) {
            embrace.logMessage(
                EmbraceEvent.Type.WARNING_LOG,
                "",
                emptyMap(),
                null,
                null,
                LogExceptionType.NONE,
                null,
                null
            )
        }
    }

    @Test
    fun testLogError() {
        impl.logError("", emptyMap(), null, false)
        verify(exactly = 1) {
            embrace.logMessage(
                EmbraceEvent.Type.ERROR_LOG,
                "",
                emptyMap(),
                null,
                null,
                LogExceptionType.NONE,
                null,
                null
            )
        }
    }

    @Test
    fun testLogHandledException() {
        val exception = Throwable("handled exception")
        impl.logHandledException(exception, LogType.ERROR, emptyMap(), null)
        verify(exactly = 1) {
            embrace.logMessage(
                EmbraceEvent.Type.ERROR_LOG,
                "handled exception",
                emptyMap(),
                exception.stackTrace,
                null,
                LogExceptionType.NONE,
                null,
                null
            )
        }
    }

    @Test
    fun testCompletedNetworkRequest() {
        mockkStatic(Uri::class)
        mockkStatic(URLUtil::class)
        every { Uri.parse("https://google.com") } returns mockk(relaxed = true)
        every { URLUtil.isHttpsUrl("https://google.com") } returns true
        impl.recordCompletedNetworkRequest(
            "https://google.com",
            "get",
            15092342340,
            15092342799,
            140,
            2509,
            200,
            null,
            null
        )
        val captor = slot<EmbraceNetworkRequest>()
        verify(exactly = 1) {
            embrace.recordNetworkRequest(capture(captor))
        }

        val request = captor.captured
        assertEquals("https://google.com", request.url)
        assertEquals(HttpMethod.GET.name, request.httpMethod)
        assertEquals(15092342340L, request.startTime)
        assertEquals(15092342799L, request.endTime)
        assertEquals(140L, request.bytesSent)
        assertEquals(2509L, request.bytesReceived)
        assertEquals(200, request.responseCode)
        assertNull(request.error)
        assertNull(request.networkCaptureData)
    }

    @Test
    fun testIncompleteNetworkRequest() {
        mockkStatic(Uri::class)
        mockkStatic(URLUtil::class)
        every { Uri.parse("https://google.com") } returns mockk(relaxed = true)
        every { URLUtil.isHttpsUrl("https://google.com") } returns true

        val exc = RuntimeException("Whoops")
        impl.recordIncompleteNetworkRequest(
            "https://google.com",
            "get",
            15092342340L,
            15092342799L,
            exc,
            "id-123",
            null
        )
        val captor = slot<EmbraceNetworkRequest>()
        verify(exactly = 1) {
            embrace.recordNetworkRequest(capture(captor))
        }

        val request = captor.captured
        assertEquals("https://google.com", request.url)
        assertEquals(HttpMethod.GET.name, request.httpMethod)
        assertEquals(15092342340L, request.startTime)
        assertEquals(15092342799L, request.endTime)
        assertNull(request.error)
        assertEquals("id-123", request.traceId)
        assertNull(request.networkCaptureData)
    }

    @Test
    fun testRecordAndDeduplicateNetworkRequest() {
        val url = "https://embrace.io"
        val callId = "testID"
        val captor = slot<EmbraceNetworkRequest>()
        val networkRequest: EmbraceNetworkRequest = mockk()
        every { networkRequest.url } answers { url }

        impl.recordAndDeduplicateNetworkRequest(callId, networkRequest)

        verify(exactly = 1) {
            embrace.recordAndDeduplicateNetworkRequest(callId, capture(captor))
        }

        assertEquals(url, captor.captured.url)
    }

    @Test
    fun `check usage of SDK time`() {
        assertEquals(beforeObjectInitTime, impl.getSdkCurrentTime())
        assertTrue(impl.getSdkCurrentTime() < System.currentTimeMillis())
        fakeClock.tick(10L)
        assertEquals(fakeClock.now(), impl.getSdkCurrentTime())
    }

    @Test
    fun `check default SDK time implementation`() {
        assertTrue(beforeObjectInitTime < defaultImpl.getSdkCurrentTime())
        assertTrue(defaultImpl.getSdkCurrentTime() <= System.currentTimeMillis())
    }

    @Test
    fun `test isInternalNetworkCaptureDisabled`() {
        assertFalse(impl.isInternalNetworkCaptureDisabled())
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = true
        assertTrue(impl.isInternalNetworkCaptureDisabled())
        assertFalse(defaultImpl.isInternalNetworkCaptureDisabled())
    }

    companion object {
        val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
