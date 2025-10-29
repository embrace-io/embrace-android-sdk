package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeInstrumentationApi
import io.embrace.android.embracesdk.fakes.FakeNetworkRequestApi
import io.embrace.android.embracesdk.fakes.FakeSdkStateApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class HucTestHarness(
    sdkEnabled: Boolean = true,
) {
    var fakeSdkStateApi: FakeSdkStateApi = FakeSdkStateApi(isStarted = sdkEnabled)
    var fakeInstrumentationApi: FakeInstrumentationApi = FakeInstrumentationApi(sdkTimeMs = FAKE_TIME_MS)
    var fakeNetworkRequestApi: FakeNetworkRequestApi = FakeNetworkRequestApi()
    var fakeInternalInterface: FakeEmbraceInternalInterface = FakeEmbraceInternalInterface()
    var mockWrappedConnection: HttpsURLConnection =
        mockk<HttpsURLConnection>(relaxed = true).apply {
            every { url } returns testUrl
            every { requestMethod } returns "GET"
            every { responseCode } answers {
                fakeInstrumentationApi.sdkTimeMs++
                200
            }
            every { getRequestProperty(any()) } returns null
        }
    var instrumentedConnection: InstrumentedHttpsURLConnection = InstrumentedHttpsURLConnection(
        wrappedConnection = mockWrappedConnection,
        sdkStateApi = fakeSdkStateApi,
        instrumentationApi = fakeInstrumentationApi,
        networkRequestApi = fakeNetworkRequestApi,
        internalInterface = fakeInternalInterface
    )

    fun runTest(test: HucTestHarness.() -> Unit) = test()

    fun assertSingleClientError(
        expectedStartTime: Long = FAKE_TIME_MS,
        expectedEndTime: Long = FAKE_TIME_MS,
        expectedUrl: String = testUrl.toString(),
        expectedMethod: String = "GET",
    ) {
        fakeNetworkRequestApi.requests.single().assertClientError(
            expectedStartTime = expectedStartTime,
            expectedEndTime = expectedEndTime,
            expectedUrl = expectedUrl,
            expectedMethod = expectedMethod,
        )
    }

    fun assertSingleSuccessfulRequest(
        expectedStartTime: Long = FAKE_TIME_MS,
        expectedEndTime: Long = FAKE_TIME_MS + 1L,
        expectedResponseCode: Int = 200,
        expectedUrl: String = testUrl.toString(),
        expectedMethod: String = "GET",
    ) {
        fakeNetworkRequestApi.requests.single().asserSuccessfulRequest(
            expectedStartTime = expectedStartTime,
            expectedEndTime = expectedEndTime,
            expectedResponseCode = expectedResponseCode,
            expectedUrl = expectedUrl,
            expectedMethod = expectedMethod,
        )
    }

    fun assertNoRequestRecorded() {
        assertEquals(0, fakeNetworkRequestApi.requests.size)
    }

    private fun EmbraceNetworkRequest.asserSuccessfulRequest(
        expectedStartTime: Long,
        expectedEndTime: Long,
        expectedResponseCode: Int,
        expectedUrl: String,
        expectedMethod: String,
    ) {
        assertEquals(expectedResponseCode, responseCode)
        assertEquals(expectedUrl, url)
        assertEquals(expectedMethod, httpMethod)
        assertEquals(expectedStartTime, startTime)
        assertEquals(expectedEndTime, endTime)
        assertNull(errorType)
        assertNull(errorMessage)
    }

    private fun EmbraceNetworkRequest.assertClientError(
        expectedStartTime: Long,
        expectedEndTime: Long,
        expectedUrl: String,
        expectedMethod: String,
    ) {
        assertNull(responseCode)
        assertEquals(expectedUrl, url)
        assertEquals(expectedMethod, httpMethod)
        assertEquals(FakeIOException::class.java.canonicalName, errorType)
        assertEquals("Nope", errorMessage)
        assertEquals(expectedStartTime, startTime)
        assertEquals(expectedEndTime, endTime)
    }
}

internal const val FAKE_TIME_MS = 1609459200000L
internal const val FAKE_FIELD_NAME = "fakeField"
internal const val FAKE_VALUE = "fakeValue"
internal val testUrl = URL("https://fakeurl.pizza/test/xyz?doStuff=true")

internal class FakeIOException : IOException("Nope")
