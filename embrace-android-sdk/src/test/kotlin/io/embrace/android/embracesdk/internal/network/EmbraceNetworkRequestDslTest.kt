package io.embrace.android.embracesdk.internal.network

import io.embrace.android.embracesdk.internal.instrumentation.network.DefaultTraceparentGenerator
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val URL = "http://google.com"
private const val START_TIME = 1600000000000
private const val END_TIME = 1600000000243
private const val BYTES_SENT = 509L
private const val BYTES_RECEIVED = 210L
private const val RESPONSE_CODE = 304
private const val TRACE_ID = "trace-id"
private const val ERR_TYPE = "err_type"
private const val ERR_MSG = "err_msg"
private val traceParent = DefaultTraceparentGenerator.generateW3cTraceparent()

internal class EmbraceNetworkRequestDslTest {

    @Test
    fun `a request with only required properties leaves the rest null`() {
        val request = EmbraceNetworkRequestDsl(URL, HttpMethod.GET, START_TIME, END_TIME).build()
        assertEquals(URL, request.url)
        assertEquals("GET", request.httpMethod)
        assertEquals(START_TIME, request.startTime)
        assertEquals(END_TIME, request.endTime)
        assertNull(request.bytesSent)
        assertNull(request.bytesReceived)
        assertNull(request.responseCode)
        assertNull(request.errorType)
        assertNull(request.errorMessage)
        assertNull(request.traceId)
        assertNull(request.w3cTraceparent)
        assertNull(request.networkCaptureData)
    }

    @Test
    fun `a completed request keeps every property set`() {
        val captureData = NetworkCaptureData(null, null, null, null, null)
        val request = EmbraceNetworkRequestDsl(URL, HttpMethod.PATCH, START_TIME, END_TIME).apply {
            traceId = TRACE_ID
            w3cTraceparent = traceParent
            networkCaptureData = captureData
            completed {
                statusCode = RESPONSE_CODE
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
            }
        }.build()
        assertEquals("PATCH", request.httpMethod)
        assertEquals(BYTES_SENT, request.bytesSent)
        assertEquals(BYTES_RECEIVED, request.bytesReceived)
        assertEquals(RESPONSE_CODE, request.responseCode)
        assertEquals(TRACE_ID, request.traceId)
        assertEquals(traceParent, request.w3cTraceparent)
        assertEquals(captureData, request.networkCaptureData)
        assertNull(request.errorType)
        assertNull(request.errorMessage)
    }

    @Test
    fun `only the last of completed and incomplete is kept`() {
        val incomplete = EmbraceNetworkRequestDsl(URL, HttpMethod.GET, START_TIME, END_TIME).apply {
            completed {
                statusCode = RESPONSE_CODE
                bytesSent = BYTES_SENT
                bytesReceived = BYTES_RECEIVED
            }
            incomplete {
                errorType = ERR_TYPE
                errorMessage = ERR_MSG
            }
        }.build()
        assertEquals(ERR_TYPE, incomplete.errorType)
        assertEquals(ERR_MSG, incomplete.errorMessage)
        assertNull(incomplete.responseCode)
        assertNull(incomplete.bytesSent)
        assertNull(incomplete.bytesReceived)

        val completed = EmbraceNetworkRequestDsl(URL, HttpMethod.GET, START_TIME, END_TIME).apply {
            incomplete {
                errorType = ERR_TYPE
                errorMessage = ERR_MSG
            }
            completed {
                statusCode = RESPONSE_CODE
            }
        }.build()
        assertEquals(RESPONSE_CODE, completed.responseCode)
        assertNull(completed.errorType)
        assertNull(completed.errorMessage)
    }

    @Test
    fun testHttpMethodFromString() {
        assertEquals(HttpMethod.PATCH, HttpMethod.fromString("patch"))
        assertEquals(HttpMethod.GET, HttpMethod.fromString("PROPFIND"))
    }
}
