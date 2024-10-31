@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketException

internal class NoopEmbraceInternalInterfaceTest {

    private lateinit var impl: NoopEmbraceInternalInterface

    @Before
    fun setUp() {
        impl = NoopEmbraceInternalInterface(
            mockk(relaxed = true)
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `check no errors thrown when invoked`() {
        impl.logInfo("", emptyMap())
        impl.logWarning("", emptyMap(), null)
        impl.logError("", emptyMap(), null, false)
        impl.logHandledException(Throwable("handled exception"), LogType.ERROR, emptyMap(), null)
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
        val request = EmbraceNetworkRequest.fromCompletedRequest(
            "https://embrace.io",
            HttpMethod.GET,
            15092342340L,
            15092342799L,
            140L,
            2509L,
            200
        )
        impl.recordNetworkRequest(request)
        impl.recordIncompleteNetworkRequest(
            "https://google.com",
            "get",
            15092342340L,
            15092342799L,
            RuntimeException("Whoops"),
            "id-123",
            null
        )
        impl.logInternalError(SocketException())
        impl.logInternalError("err", "message")
        impl.stopSdk()
    }

    @Test
    fun `check default SDK time implementation`() {
        assertTrue(beforeObjectInitTime < impl.getSdkCurrentTime())
        assertTrue(impl.getSdkCurrentTime() <= System.currentTimeMillis())
    }

    @Test
    fun `check isNetworkSpanForwardingEnabled before SDK starts`() {
        assertFalse(impl.isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `check isAnrCaptureEnabled before SDK starts`() {
        assertFalse(impl.isAnrCaptureEnabled())
    }

    @Test
    fun `check isNdkEnabled before SDK starts`() {
        assertFalse(impl.isNdkEnabled())
    }

    companion object {
        private val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
