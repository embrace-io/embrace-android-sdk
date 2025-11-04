package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.instrumentation.HucLiteDataSource
import io.embrace.opentelemetry.kotlin.semconv.ErrorAttributes
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.HttpAttributes
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class HucTestHarness {
    val fakeTelemetryDestination: FakeTelemetryDestination = FakeTelemetryDestination()
    val fakeClock = FakeClock(FAKE_TIME_MS)
    val fakeEmbLogger = FakeEmbLogger(throwOnInternalError = false)
    val hucLiteDataSource = HucLiteDataSource(
        FakeInstrumentationArgs(
            application = mockk(),
            destination = fakeTelemetryDestination,
            logger = fakeEmbLogger,
            clock = fakeClock,
        )
    )

    var mockWrappedConnection: HttpsURLConnection =
        mockk<HttpsURLConnection>(relaxed = true).apply {
            every { url } returns testUrl
            every { requestMethod } returns "GET"
            every { responseCode } answers {
                moveTimeForward()
                200
            }
            every { getRequestProperty(any()) } returns null
        }
    var instrumentedConnection: InstrumentedHttpsURLConnection = InstrumentedHttpsURLConnection(
        wrappedConnection = mockWrappedConnection,
        clock = fakeClock,
        hucLiteDataSource = hucLiteDataSource,
    )

    fun runTest(test: HucTestHarness.() -> Unit) = test()

    fun getCurrentTimeMs(): Long = fakeClock.now()

    fun moveTimeForward(increment: Long = 1L): Long = fakeClock.tick(increment)

    fun assertSingleClientError(
        expectedStartTime: Long = FAKE_TIME_MS,
        expectedEndTime: Long = FAKE_TIME_MS,
        expectedUrl: String = testUrl.toString(),
        expectedMethod: String = "GET",
    ) {
        fakeTelemetryDestination.createdSpans.single().assertClientError(
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
        fakeTelemetryDestination.createdSpans.single().assertSuccessfulRequest(
            expectedStartTime = expectedStartTime,
            expectedEndTime = expectedEndTime,
            expectedResponseCode = expectedResponseCode,
            expectedUrl = expectedUrl,
            expectedMethod = expectedMethod,
        )
    }

    fun assertNoRequestRecorded() {
        assertEquals(0, fakeTelemetryDestination.createdSpans.size)
    }

    fun getInternalErrors() = fakeEmbLogger.internalErrorMessages

    private fun FakeSpanToken.assertSuccessfulRequest(
        expectedStartTime: Long,
        expectedEndTime: Long,
        expectedResponseCode: Int,
        expectedUrl: String,
        expectedMethod: String,
    ) {
        assertEquals(expectedResponseCode.toString(), attributes[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
        if (expectedResponseCode !in 1..400) {
            assertEquals("failure", errorCode)
        } else {
            assertNull(errorCode)
        }
        assertEquals(expectedUrl, attributes["url.full"])
        assertEquals(expectedMethod, attributes[HttpAttributes.HTTP_REQUEST_METHOD])
        assertEquals(expectedStartTime, startTimeMs)
        assertEquals(expectedEndTime, endTimeMs)

        assertFalse(attributes.containsKey(ErrorAttributes.ERROR_TYPE))
        assertFalse(attributes.containsKey(ExceptionAttributes.EXCEPTION_MESSAGE))
    }

    private fun FakeSpanToken.assertClientError(
        expectedStartTime: Long,
        expectedEndTime: Long,
        expectedUrl: String,
        expectedMethod: String,
    ) {
        assertNull(attributes[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
        assertEquals(expectedUrl, attributes["url.full"])
        assertEquals(expectedMethod, attributes[HttpAttributes.HTTP_REQUEST_METHOD])
        assertEquals(expectedStartTime, startTimeMs)
        assertEquals(expectedEndTime, endTimeMs)
        assertEquals(FakeIOException::class.java.canonicalName, attributes[ErrorAttributes.ERROR_TYPE])
        assertEquals("Nope", attributes[ExceptionAttributes.EXCEPTION_MESSAGE])
        assertEquals(expectedStartTime, startTimeMs)
        assertEquals(expectedEndTime, endTimeMs)
    }
}

internal const val FAKE_TIME_MS = DEFAULT_FAKE_CURRENT_TIME
internal const val FAKE_FIELD_NAME = "fakeField"
internal const val FAKE_VALUE = "fakeValue"
internal val testUrl = URL("https://fakeurl.pizza/test/xyz?doStuff=true")

internal class FakeIOException : IOException("Nope")
