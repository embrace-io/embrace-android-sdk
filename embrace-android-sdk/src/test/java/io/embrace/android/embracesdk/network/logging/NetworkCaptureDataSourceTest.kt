package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.fakeNetworkCapturedCall
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NetworkCaptureDataSourceTest {

    @Test
    fun `test network capture is sent as log`() {
        val logWriter = FakeLogWriter()
        val dataSource = NetworkCaptureDataSourceImpl(
            logWriter,
            EmbLoggerImpl()
        )
        val capturedCall = fakeNetworkCapturedCall()
        dataSource.logNetworkCapturedCall(capturedCall)

        assertEquals(1, logWriter.logEvents.size)
        val log = logWriter.logEvents[0]
        assertEquals(SchemaType.NetworkCapturedRequest::class.java, log.schemaType.javaClass)
        assertEquals(100L, capturedCall.duration)
        assertEquals(1713453000L, capturedCall.endTime)
        assertEquals("GET", capturedCall.httpMethod)
        assertEquals("httpbin.*", capturedCall.matchedUrl)
        assertEquals("body", capturedCall.requestBody)
        assertEquals(10, capturedCall.requestBodySize)
        assertEquals("id", capturedCall.networkId)
        assertEquals("query", capturedCall.requestQuery)
        assertEquals(mapOf("query-header" to "value"), capturedCall.requestQueryHeaders)
        assertEquals(5, capturedCall.requestSize)
        assertEquals("response", capturedCall.responseBody)
        assertEquals(8, capturedCall.responseBodySize)
        assertEquals(mapOf("response-header" to "value"), capturedCall.responseHeaders)
        assertEquals(300, capturedCall.responseSize)
        assertEquals(200, capturedCall.responseStatus)
        assertEquals("session-id", capturedCall.sessionId)
        assertEquals(1713452000L, capturedCall.startTime)
        assertEquals("https://httpbin.org/get", capturedCall.url)
        assertEquals("", capturedCall.errorMessage)
        assertEquals("encrypted-payload", capturedCall.encryptedPayload)
    }
}
