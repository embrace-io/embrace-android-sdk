package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.internal.TraceparentGenerator
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.network.http.NetworkCaptureData
import org.junit.Assert.assertEquals
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
private val httpMethod = HttpMethod.GET
private val traceParent = TraceparentGenerator.generateW3CTraceparent()

@Suppress("DEPRECATION")
internal class EmbraceNetworkRequestTest {

    @Test
    fun testFromCompletedRequest1() {
        val request = EmbraceNetworkRequest.fromCompletedRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            BYTES_SENT,
            BYTES_RECEIVED,
            RESPONSE_CODE
        )
        verifyDefaultCompletedRequest(request)
    }

    @Test
    fun testFromCompletedRequest2() {
        val request = EmbraceNetworkRequest.fromCompletedRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            BYTES_SENT,
            BYTES_RECEIVED,
            RESPONSE_CODE,
            TRACE_ID
        )
        verifyDefaultCompletedRequest(request)
        assertEquals(TRACE_ID, request.traceId)
    }

    @Test
    fun testFromCompletedRequest3() {
        val captureData = NetworkCaptureData(null, null, null, null, null)
        val request = EmbraceNetworkRequest.fromCompletedRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            BYTES_SENT,
            BYTES_RECEIVED,
            RESPONSE_CODE,
            TRACE_ID,
            captureData
        )
        verifyDefaultCompletedRequest(request)
        assertEquals(TRACE_ID, request.traceId)
        assertEquals(captureData, request.networkCaptureData)
    }

    @Test
    fun testFromCompletedRequest4() {
        val captureData = NetworkCaptureData(null, null, null, null, null)
        val request = EmbraceNetworkRequest.fromCompletedRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            BYTES_SENT,
            BYTES_RECEIVED,
            RESPONSE_CODE,
            TRACE_ID,
            traceParent,
            captureData
        )
        verifyDefaultCompletedRequest(request)
        assertEquals(TRACE_ID, request.traceId)
        assertEquals(traceParent, request.w3cTraceparent)
        assertEquals(captureData, request.networkCaptureData)
    }

    @Test
    fun testFromIncompleteRequest1() {
        val request = EmbraceNetworkRequest.fromIncompleteRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            ERR_TYPE,
            ERR_MSG
        )
        verifyDefaultIncompleteRequest(request)
    }

    @Test
    fun testFromIncompleteRequest2() {
        val request = EmbraceNetworkRequest.fromIncompleteRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            ERR_TYPE,
            ERR_MSG,
            TRACE_ID
        )
        verifyDefaultIncompleteRequest(request)
        assertEquals(TRACE_ID, request.traceId)
    }

    @Test
    fun testFromIncompleteRequest3() {
        val captureData = NetworkCaptureData(null, null, null, null, null)
        val request = EmbraceNetworkRequest.fromIncompleteRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            ERR_TYPE,
            ERR_MSG,
            TRACE_ID,
            captureData
        )
        verifyDefaultIncompleteRequest(request)
        assertEquals(TRACE_ID, request.traceId)
        assertEquals(captureData, request.networkCaptureData)
    }

    @Test
    fun testFromIncompleteRequest4() {
        val captureData = NetworkCaptureData(null, null, null, null, null)
        val request = EmbraceNetworkRequest.fromIncompleteRequest(
            URL,
            httpMethod,
            START_TIME,
            END_TIME,
            ERR_TYPE,
            ERR_MSG,
            TRACE_ID,
            traceParent,
            captureData
        )
        verifyDefaultIncompleteRequest(request)
        assertEquals(TRACE_ID, request.traceId)
        assertEquals(traceParent, request.w3cTraceparent)
        assertEquals(captureData, request.networkCaptureData)
    }

    private fun verifyDefaultCompletedRequest(request: EmbraceNetworkRequest) {
        assertEquals(URL, request.url)
        assertEquals(httpMethod.name, request.httpMethod)
        assertEquals(START_TIME, request.startTime)
        assertEquals(END_TIME, request.endTime)
        assertEquals(BYTES_SENT, request.bytesSent)
        assertEquals(BYTES_RECEIVED, request.bytesReceived)
        assertEquals(RESPONSE_CODE, request.responseCode)
    }

    private fun verifyDefaultIncompleteRequest(request: EmbraceNetworkRequest) {
        assertEquals(URL, request.url)
        assertEquals(httpMethod.name, request.httpMethod)
        assertEquals(START_TIME, request.startTime)
        assertEquals(END_TIME, request.endTime)
        assertEquals(ERR_TYPE, request.errorType)
        assertEquals(ERR_MSG, request.errorMessage)
    }
}
