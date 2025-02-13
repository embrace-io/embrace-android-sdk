package io.embrace.android.gradle.plugin.ndk

import io.embrace.android.gradle.plugin.network.NetworkService
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshake
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeRequest
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeResponse
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NdkUploadHandshakeTest {

    private lateinit var mockNetworkService: NetworkService
    private lateinit var ndkUploadHandshakeRequest: NdkUploadHandshakeRequest
    private lateinit var ndkUploadHandshake: NdkUploadHandshake

    @Before
    fun setUp() {
        mockNetworkService = mockk()
        ndkUploadHandshake = NdkUploadHandshake(
            networkService = mockNetworkService
        )

        ndkUploadHandshakeRequest = NdkUploadHandshakeRequest(
            appId = APP_ID,
            apiToken = API_TOKEN,
            variant = VARIANT,
            archSymbols = emptyMap()
        )
    }

    @Test
    fun `NdkUploadHandshakeRequest serialization test - empty archs`() {
        val request = NdkUploadHandshakeRequest(
            appId = APP_ID,
            apiToken = API_TOKEN,
            variant = VARIANT,
            archSymbols = emptyMap()
        )
        val expectedRequest = """
            {
              "app": "$APP_ID",
              "token": "$API_TOKEN",
              "variant": "$VARIANT",
              "archs": {}
            }
        """.trim().replace("\\s".toRegex(), "")
        val serializedRequest = MoshiSerializer().toJson(request)
        assertEquals(expectedRequest, serializedRequest)
    }

    @Test
    fun `NdkUploadHandshakeRequest serialization test - with symbols`() {
        val request = NdkUploadHandshakeRequest(
            appId = APP_ID,
            apiToken = API_TOKEN,
            variant = VARIANT,
            archSymbols = mapOf(
                "x86_64" to mapOf("libnative.so" to "f067beecae4f901c81c642d002810944460efd7b"),
                "x86" to mapOf("libnative.so" to "3c84ca4cf150a346db8d195426e520b7a45a0118"),
                "armeabi-v7a" to mapOf("libnative.so" to "b621b4bac764b4a1d6166984d63d9958187439a6"),
                "arm64-v8a" to mapOf("libnative.so" to "7d8c51cd16d00a369a1b923e1e9aed88c501beee")
            )
        )
        val expectedRequest = """
            {
              "app": "$APP_ID",
              "token": "$API_TOKEN",
              "variant": "$VARIANT",
              "archs": {
                "x86_64": {
                  "libnative.so": "f067beecae4f901c81c642d002810944460efd7b"
                },
                "x86": {
                  "libnative.so": "3c84ca4cf150a346db8d195426e520b7a45a0118"
                },
                "armeabi-v7a": {
                  "libnative.so": "b621b4bac764b4a1d6166984d63d9958187439a6"
                },
                "arm64-v8a": {
                  "libnative.so": "7d8c51cd16d00a369a1b923e1e9aed88c501beee"
                }
              }
            }
        """.trim().replace("\\s".toRegex(), "")
        val serializedRequest = MoshiSerializer().toJson(request)
        assertEquals(expectedRequest, serializedRequest)
    }

    @Test
    fun `NdkUploadHandshakeResponse deserialization test - empty symbols`() {
        val response = """
            {
              "archs": {}
            }
        """.trim().replace("\\s".toRegex(), "")
        val deserializedResponse =
            MoshiSerializer().fromJson(response, NdkUploadHandshakeResponse::class.java)
        assertNotNull(deserializedResponse.symbols)
        assertTrue(deserializedResponse.symbols!!.isEmpty())
    }

    @Test
    fun `NdkUploadHandshakeResponse deserialization test - with symbols`() {
        val response = """
            {
              "archs": {
                "arm64-v8a": [
                  "libnative.so"
                ],
                "armeabi-v7a": [
                  "libnative.so"
                ],
                "x86": [
                  "libnative.so"
                ],
                "x86_64": [
                  "libnative.so"
                ]
              }
            }
        """.replace("\\s".toRegex(), "")

        val deserializedResponse =
            MoshiSerializer().fromJson(response, NdkUploadHandshakeResponse::class.java)
        val expectedResponse = NdkUploadHandshakeResponse(
            symbols = mapOf(
                "x86" to listOf("libnative.so"),
                "x86_64" to listOf("libnative.so"),
                "armeabi-v7a" to listOf("libnative.so"),
                "arm64-v8a" to listOf("libnative.so")
            )
        )
        assertEquals(expectedResponse, deserializedResponse)
    }
}

private const val APP_ID = "appId"
private const val API_TOKEN = "apiToken"
private const val VARIANT = "variant"
