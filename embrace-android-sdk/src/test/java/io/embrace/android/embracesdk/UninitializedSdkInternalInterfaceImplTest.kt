package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketException

internal class UninitializedSdkInternalInterfaceImplTest {

    private lateinit var impl: UninitializedSdkInternalInterfaceImpl
    private lateinit var initModule: FakeInitModule
    private lateinit var openTelemetryModule: OpenTelemetryModule

    @Before
    fun setUp() {
        initModule = FakeInitModule(clock = FakeClock(currentTime = beforeObjectInitTime))
        openTelemetryModule = initModule.openTelemetryModule
        impl = UninitializedSdkInternalInterfaceImpl(
            InternalTracer(
                openTelemetryModule.spansRepository,
                openTelemetryModule.embraceTracer
            )
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
        impl.recordAndDeduplicateNetworkRequest("testID", request)
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
    fun `check isInternalNetworkCaptureDisabled before SDK starts`() {
        assertFalse(impl.isInternalNetworkCaptureDisabled())
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
