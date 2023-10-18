package io.embrace.android.embracesdk

import android.net.Uri
import android.webkit.URLUtil
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var impl: EmbraceInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        impl = EmbraceInternalInterfaceImpl(embrace)
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
    fun testAddBreadcrumb() {
        impl.addBreadcrumb("")
        verify(exactly = 1) { embrace.addBreadcrumb("") }
    }

    @Test
    fun testGetDeviceId() {
        every { embrace.deviceId } returns "test"
        assertEquals("test", impl.deviceId)
    }

    @Test
    fun testSetUserIdentifier() {
        impl.setUserIdentifier("")
        verify(exactly = 1) { embrace.setUserIdentifier("") }
    }

    @Test
    fun testClearUserIdentifier() {
        impl.clearUserIdentifier()
        verify(exactly = 1) { embrace.clearUserIdentifier() }
    }

    @Test
    fun testSetUsername() {
        impl.setUsername("")
        verify(exactly = 1) { embrace.setUsername("") }
    }

    @Test
    fun testClearUsername() {
        impl.clearUsername()
        verify(exactly = 1) { embrace.clearUsername() }
    }

    @Test
    fun testSetUserEmail() {
        impl.setUserEmail("")
        verify(exactly = 1) { embrace.setUserEmail("") }
    }

    @Test
    fun testClearUserEmail() {
        impl.clearUserEmail()
        verify(exactly = 1) { embrace.clearUserEmail() }
    }

    @Test
    fun testSetUserAsPayer() {
        impl.setUserAsPayer()
        verify(exactly = 1) { embrace.setUserAsPayer() }
    }

    @Test
    fun testClearUserAsPayer() {
        impl.clearUserAsPayer()
        verify(exactly = 1) { embrace.clearUserAsPayer() }
    }

    @Test
    fun testAddUserPersona() {
        impl.addUserPersona("")
        verify(exactly = 1) { embrace.addUserPersona("") }
    }

    @Test
    fun testClearUserPersona() {
        impl.clearUserPersona("")
        verify(exactly = 1) { embrace.clearUserPersona("") }
    }

    @Test
    fun testClearAllUserPersonas() {
        impl.clearAllUserPersonas()
        verify(exactly = 1) { embrace.clearAllUserPersonas() }
    }

    @Test
    fun testAddSessionProperty() {
        impl.addSessionProperty("key", "value", true)
        verify(exactly = 1) { embrace.addSessionProperty("key", "value", true) }
    }

    @Test
    fun testRemoveSessionProperty() {
        impl.removeSessionProperty("key")
        verify(exactly = 1) { embrace.removeSessionProperty("key") }
    }

    @Test
    fun testGetSessionProperties() {
        every { embrace.sessionProperties } returns mapOf()
        assertEquals(mapOf<String, String>(), impl.sessionProperties)
    }

    @Test
    fun testStartMoment() {
        impl.startMoment("name", "id", mapOf())
        verify(exactly = 1) { embrace.startMoment("name", "id", mapOf()) }
    }

    @Test
    fun testEndMoment() {
        impl.endMoment("name", "id", mapOf())
        verify(exactly = 1) { embrace.endMoment("name", "id", mapOf()) }
    }

    @Test
    fun testStartView() {
        impl.startView("")
        verify(exactly = 1) { embrace.startView("") }
    }

    @Test
    fun testEndView() {
        impl.endView("")
        verify(exactly = 1) { embrace.endView("") }
    }

    @Test
    fun testEndAppStartup() {
        impl.endAppStartup(emptyMap())
        verify(exactly = 1) { embrace.endAppStartup(emptyMap()) }
    }

    @Test
    fun testLogInternalError() {
        impl.logInternalError("msg", "details")
        verify(exactly = 1) { embrace.logInternalError("msg", "details") }
    }

    @Test
    fun testEndSession() {
        impl.endSession(true)
        verify(exactly = 1) { embrace.endSession(true) }
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
}
