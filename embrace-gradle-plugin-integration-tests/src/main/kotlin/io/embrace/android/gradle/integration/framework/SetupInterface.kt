package io.embrace.android.gradle.integration.framework

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeResponse
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer
import kotlinx.serialization.SerializationStrategy
import okhttp3.mockwebserver.MockResponse

class SetupInterface(
    private val apiServer: FakeApiServer,
) {

    private val serializer = JsonSerializer()

    /**
     * Serializes the object into a string
     */
    fun <T> serializeRequestBody(obj: T, strategy: SerializationStrategy<T>): String {
        return serializer.toJson(obj, strategy)
    }

    fun enqueueResponse(endpoint: EmbraceEndpoint, response: MockResponse) {
        apiServer.enqueueResponse(endpoint, response)
    }

    fun SetupInterface.setupMockResponses(
        expectedLibs: List<String>,
        expectedArchs: List<String>,
        expectedVariants: List<String>,
    ) {
        val requestedSymbols = expectedArchs.associateWith { expectedLibs }
        val json =
            serializeRequestBody(NdkUploadHandshakeResponse(requestedSymbols), NdkUploadHandshakeResponse.serializer())
        val response = MockResponse().setBody(json)
        repeat(expectedVariants.size) {
            enqueueResponse(EmbraceEndpoint.NDK_HANDSHAKE, response)
        }
    }

    fun SetupInterface.setupResponseWithMalformedBody(endpoint: EmbraceEndpoint) {
        val response = MockResponse().setBody("invalid json")
        enqueueResponse(endpoint, response)
    }

    fun SetupInterface.setupEmptyHandshakeResponse() {
        val json = serializeRequestBody(NdkUploadHandshakeResponse(null), NdkUploadHandshakeResponse.serializer())
        enqueueResponse(EmbraceEndpoint.NDK_HANDSHAKE, MockResponse().setBody(json))
    }

    fun SetupInterface.setupMapResponseWithEmptyValues(endpoint: EmbraceEndpoint) {
        val response = """
            {
              "archs": {
                "arm64-v8a": []
              }
            }
        """.replace("\\s".toRegex(), "")
        enqueueResponse(endpoint, MockResponse().setBody(response))
    }

    fun SetupInterface.setupErrorResponse(endpoint: EmbraceEndpoint) {
        val response = MockResponse().setResponseCode(400)
        enqueueResponse(endpoint, response)
    }
}
