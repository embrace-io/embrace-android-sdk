package io.embrace.android.embracesdk.comms.api

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
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
        EmbraceUrl.create("https://google.com"),
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
        val minimal = ApiRequest(url = EmbraceUrl.create("https://google.com"))
        assertTrue(minimal.getHeaders()["User-Agent"].toString().startsWith("Embrace/a/"))

        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json"
            ),
            minimal.getHeaders().minus("User-Agent")
        )
    }

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("api_request.json", request)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<ApiRequest>("api_request.json")
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
            obj.getHeaders()
        )
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<ApiRequest>()
    }

    @Test
    fun testSessionRequest() {
        assertFalse(request.isSessionRequest())

        val copy = request.copy(url = EmbraceUrl.create("https://example.com/v2/spans"))
        assertTrue(copy.isSessionRequest())
    }
}
