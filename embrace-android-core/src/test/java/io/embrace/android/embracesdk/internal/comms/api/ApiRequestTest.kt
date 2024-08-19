package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ApiRequestTest {

    private val request = ApiRequest(
        "application/json",
        "Embrace/a/1",
        "application/json",
        "application/json",
        "application/json",
        "abcde",
        "test_did",
        "test_eid",
        "test_lid",
        ApiRequestUrl("https://google.com"),
        HttpMethod.GET,
        "d800f828fec4409dcabc7f5252e7ce71"
    )

    @Test
    fun testFullHeaders() {
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/1",
                "Content-Type" to "application/json",
                "Content-Encoding" to "application/json",
                "Accept-Encoding" to "application/json",
                "X-EM-AID" to "abcde",
                "X-EM-DID" to "test_did",
                "X-EM-SID" to "test_eid",
                "X-EM-LID" to "test_lid",
                "If-None-Match" to "d800f828fec4409dcabc7f5252e7ce71"
            ),
            request.getHeaders()
        )
    }

    @Test
    fun testMinimalHeaders() {
        val minimal = ApiRequest(url = ApiRequestUrl("https://google.com"), userAgent = "test")
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "User-Agent" to "test"
            ),
            minimal.getHeaders()
        )
    }

    @Test
    fun testSessionRequest() {
        assertFalse(request.isSessionRequest())

        val copy = request.copy(url = ApiRequestUrl("https://example.com/v2/spans"))
        assertTrue(copy.isSessionRequest())
    }
}
