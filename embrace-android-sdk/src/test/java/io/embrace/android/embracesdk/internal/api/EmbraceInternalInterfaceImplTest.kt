@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.api

import android.net.Uri
import android.webkit.URLUtil
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureService
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.EmbraceInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.payload.EventType
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
import java.net.SocketException

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var internalImpl: EmbraceInternalInterfaceImpl
    private lateinit var embraceImpl: EmbraceImpl
    private lateinit var fakeClock: FakeClock
    private lateinit var initModule: FakeInitModule
    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var fakeNetworkCaptureService: FakeNetworkCaptureService
    private lateinit var fakeEventService: FakeEventService

    @Before
    fun setUp() {
        embraceImpl = mockk(relaxed = true)
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        initModule = FakeInitModule(clock = fakeClock, logger = FakeEmbLogger(false))
        fakeConfigService = FakeConfigService()
        fakeNetworkCaptureService = FakeNetworkCaptureService()
        fakeEventService = FakeEventService()
        internalImpl = EmbraceInternalInterfaceImpl(
            embraceImpl,
            initModule,
            fakeNetworkCaptureService,
            fakeEventService,
            fakeConfigService,
            initModule.openTelemetryModule.internalTracer
        )
    }

    @Test
    fun testLogInfo() {
        internalImpl.logInfo("", emptyMap())
        verify(exactly = 1) {
            embraceImpl.logMessage("", Severity.INFO, emptyMap<String, String>())
        }
    }

    @Test
    fun testLogWarning() {
        internalImpl.logWarning("", emptyMap(), null)
        verify(exactly = 1) {
            embraceImpl.logMessage("", Severity.WARNING, emptyMap<String, String>())
        }
    }

    @Test
    fun testLogError() {
        internalImpl.logError("", emptyMap(), null, false)
        verify(exactly = 1) {
            embraceImpl.logMessage("", Severity.ERROR, emptyMap<String, String>())
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testLogHandledException() {
        val exception = Throwable("handled exception")
        internalImpl.logHandledException(exception, LogType.ERROR, emptyMap(), null)
        verify(exactly = 1) {
            embraceImpl.logMessage(
                EventType.ERROR_LOG,
                "handled exception",
                emptyMap<String, String>(),
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
        internalImpl.recordCompletedNetworkRequest(
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
            embraceImpl.recordNetworkRequest(capture(captor))
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
        internalImpl.recordIncompleteNetworkRequest(
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
            embraceImpl.recordNetworkRequest(capture(captor))
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
        val captor = slot<EmbraceNetworkRequest>()
        val networkRequest: EmbraceNetworkRequest = EmbraceNetworkRequest.fromCompletedRequest(
            url,
            HttpMethod.GET,
            15092342340L,
            15092342799L,
            140L,
            2509L,
            200
        )

        internalImpl.recordNetworkRequest(networkRequest)
        verify(exactly = 1) {
            embraceImpl.recordNetworkRequest(capture(captor))
        }

        assertEquals(url, captor.captured.url)
    }

    @Test
    fun `check usage of SDK time`() {
        assertEquals(beforeObjectInitTime, internalImpl.getSdkCurrentTime())
        assertTrue(internalImpl.getSdkCurrentTime() < System.currentTimeMillis())
        fakeClock.tick(10L)
        assertEquals(fakeClock.now(), internalImpl.getSdkCurrentTime())
    }

    @Test
    fun `check isNetworkSpanForwardingEnabled`() {
        assertFalse(internalImpl.isNetworkSpanForwardingEnabled())
        fakeConfigService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        assertTrue(internalImpl.isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `check isAnrCaptureEnabled`() {
        assertTrue(internalImpl.isAnrCaptureEnabled())
        fakeConfigService.anrBehavior = FakeAnrBehavior(anrCaptureEnabled = false)
        assertFalse(internalImpl.isAnrCaptureEnabled())
        fakeConfigService.anrBehavior = FakeAnrBehavior(anrCaptureEnabled = true)
        assertTrue(internalImpl.isAnrCaptureEnabled())
    }

    @Test
    fun `check isNdkEnabled`() {
        assertFalse(internalImpl.isNdkEnabled())
        fakeConfigService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(ndkEnabled = true)
        assertTrue(internalImpl.isNdkEnabled())
    }

    @Test
    fun `check logInternalError with exception`() {
        val expectedException = SocketException()
        internalImpl.logInternalError(expectedException)
        val logger = initModule.logger as FakeEmbLogger
        checkNotNull(logger.internalErrorMessages.single().throwable)
    }

    @Test
    fun `check logInternalError with error type and message`() {
        internalImpl.logInternalError("err", "message")
        val logger = initModule.logger as FakeEmbLogger
        checkNotNull(logger.internalErrorMessages.single().throwable)
    }

    @Test
    fun `check stopping SDK`() {
        internalImpl.stopSdk()
        assertFalse(embraceImpl.isStarted)
    }

    companion object {
        private val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
