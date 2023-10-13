package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert
import org.junit.Test

internal class ApiRequestTest {

    private val serializer = EmbraceSerializer()

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
        EmbraceUrl.getUrl("https://google.com"),
        HttpMethod.GET,
        "d800f828fec4409dcabc7f5252e7ce71"
    )

    @Test
    fun testFullHeaders() {
        Assert.assertEquals(
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
        val minimal = ApiRequest(url = EmbraceUrl.getUrl("https://google.com"))
        Assert.assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/${BuildConfig.VERSION_NAME}",
                "Content-Type" to "application/json"
            ),
            minimal.getHeaders()
        )
    }

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("api_request.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(request)
        Assert.assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("api_request.json")
        val obj = serializer.fromJson(json, ApiRequest::class.java)
        Assert.assertEquals(
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
            obj?.getHeaders()
        )
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", ApiRequest::class.java)
        Assert.assertNotNull(info)
    }
}
