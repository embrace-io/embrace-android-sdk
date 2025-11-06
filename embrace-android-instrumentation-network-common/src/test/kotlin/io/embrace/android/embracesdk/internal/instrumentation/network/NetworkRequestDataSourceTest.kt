package io.embrace.android.embracesdk.internal.instrumentation.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDomainCountLimiter
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.utils.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkRequestDataSourceTest {

    private lateinit var domainCountLimiter: FakeDomainCountLimiter
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: NetworkRequestDataSource

    @Before
    fun setUp() {
        domainCountLimiter = FakeDomainCountLimiter()

        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext(),
            configService = FakeConfigService(networkBehavior = FakeNetworkBehavior(domainCountLimiter = domainCountLimiter)),
        )
        dataSource = NetworkRequestDataSourceImpl(args)
    }

    @Test
    fun `multiple network requests are recorded to the span service correctly`() {
        logNetworkRequest(url = "www.example1.com")
        logNetworkRequest(
            url = "www.example2.com",
            startTime = 200,
            endTime = 300,
            statusCode = 404,
        )
        logNetworkRequest(
            url = "www.example3.com",
            startTime = 300,
            endTime = 400,
            statusCode = 500,
        )
        logNetworkRequest(
            url = "www.example4.com",
            startTime = 400,
            endTime = 500,
            statusCode = 203,
        )
        dataSource.recordNetworkRequest(
            HttpNetworkRequest(
                url = "www.example5.com",
                httpMethod = "GET",
                startTime = 600L,
                endTime = 650L,
                errorType = "RuntimeException",
                errorMessage = ""
            )
        )

        args.destination.createdSpans

        val spans = getNetworkSpans()
        assertEquals(5, spans.size)

        val requestSpans = spans.associateBy { it.attributes["url.full"] }
        checkNotNull(requestSpans["www.example1.com"]).assertNetworkRequest(
            expectedStartTimeMs = 100L,
            expectedEndTimeMs = 200L,
        )
        checkNotNull(requestSpans["www.example2.com"]).assertNetworkRequest(
            expectedStartTimeMs = 200L,
            expectedEndTimeMs = 300L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
        checkNotNull(requestSpans["www.example3.com"]).assertNetworkRequest(
            expectedStartTimeMs = 300L,
            expectedEndTimeMs = 400L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
        checkNotNull(requestSpans["www.example4.com"]).assertNetworkRequest(
            expectedStartTimeMs = 400L,
            expectedEndTimeMs = 500L,
        )
        checkNotNull(requestSpans["www.example5.com"]).assertNetworkRequest(
            expectedStartTimeMs = 600L,
            expectedEndTimeMs = 650L,
            expectedErrorCode = ErrorCodeAttribute.Failure
        )
    }

    @Test
    fun `network requests are not recorded if the URL domain is invalid`() {
        logNetworkRequest("examplecom", 100, 200)
        assertTrue(getNetworkSpans().isEmpty())
    }

    @Test
    fun `network requests are not recorded if the domain count limiter does not allow it`() {
        domainCountLimiter.canLog = false
        logNetworkRequest("www.example.com", 100, 200)
        assertTrue(getNetworkSpans().isEmpty())
    }

    @Test
    fun `network requests with the same start time will be recorded each time`() {
        val startTime = 99L
        val endTime = 300L

        repeat(2) {
            logNetworkRequest(url = "https://embrace.io", startTime = startTime, endTime = endTime)
        }

        assertEquals(2, getNetworkSpans().size)
    }

    @Test
    fun `URL parameters will be truncated`() {
        val url = "https://www.example1.com/a?b=c"
        logNetworkRequest(url = url)

        with(checkNotNull(getNetworkSpans().single())) {
            assertEquals(
                NetworkUtils.stripUrl(url),
                attributes["url.full"]
            )
        }
    }

    private fun logNetworkRequest(
        url: String,
        startTime: Long = 100,
        endTime: Long = 200,
        statusCode: Int = 200,
        body: HttpNetworkRequest.HttpRequestBody? = null,
    ) {
        dataSource.recordNetworkRequest(
            HttpNetworkRequest(
                url = url,
                httpMethod = "GET",
                startTime = startTime,
                endTime = endTime,
                bytesSent = 100L,
                bytesReceived = 1000L,
                statusCode = statusCode,
                body = body
            )
        )
    }

    private fun getNetworkSpans(): List<FakeSpanToken> {
        return args.destination.createdSpans
            .filter { it.type == EmbType.Performance.Network }
    }

    private fun FakeSpanToken.assertNetworkRequest(
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedErrorCode: ErrorCodeAttribute? = null,
    ) {
        assertEquals(expectedStartTimeMs, startTimeMs)
        assertEquals(expectedEndTimeMs, endTimeMs)
        assertEquals(expectedErrorCode, errorCode)
    }
}
