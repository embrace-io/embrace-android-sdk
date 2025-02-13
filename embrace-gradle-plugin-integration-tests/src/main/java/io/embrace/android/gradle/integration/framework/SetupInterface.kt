package io.embrace.android.gradle.integration.framework

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeResponse
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import okhttp3.mockwebserver.MockResponse

class SetupInterface(
    private val apiServer: FakeApiServer
) {

    val moshiSerializer = MoshiSerializer()

    /**
     * Serializes the object into a string
     */
    inline fun <reified T> serializeRequestBody(obj: T): String {
        return moshiSerializer.toJson(obj, T::class.java)
    }

    fun enqueueResponse(endpoint: EmbraceEndpoint, response: MockResponse) {
        apiServer.enqueueResponse(endpoint, response)
    }

    fun SetupInterface.setupMockResponses(
        expectedLibs: List<String>,
        expectedArchs: List<String>,
        expectedVariants: List<String>
    ) {
        val requestedSymbols = expectedArchs.associateWith { expectedLibs }
        val json = serializeRequestBody(NdkUploadHandshakeResponse(requestedSymbols))
        val response = MockResponse().setBody(json)
        repeat(expectedVariants.size) {
            enqueueResponse(EmbraceEndpoint.NDK_HANDSHAKE, response)
        }
    }
}
