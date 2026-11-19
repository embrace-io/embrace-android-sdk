package io.embrace.android.testapiserver

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * The API of Embrace's server backend that the SDK calls. A request calls the [SdkBackendEndpoint] with its method
 * and a path ending `/v2/<segment>`, after whatever base URL the SDK was given. [config] answers the config fetch, and
 * every delivery gets 200. [onRequest] sees each request it answers, before answering it.
 */
class SdkBackendApi(
    private val config: RemoteConfigEndpoint,
    private val onRequest: (SdkBackendEndpoint, RecordedRequest) -> Unit = { _, _ -> },
) : FakeBackendApi<SdkBackendEndpoint> {

    override fun endpointOf(request: RecordedRequest): SdkBackendEndpoint? {
        val path = request.requestUrl?.encodedPath ?: return null
        return SdkBackendEndpoint.entries.firstOrNull {
            it.method == request.method && path.endsWith("/$API_VERSION/${it.pathSegment}")
        }
    }

    override fun respond(endpoint: SdkBackendEndpoint, request: RecordedRequest): MockResponse {
        onRequest(endpoint, request)
        return if (endpoint == SdkBackendEndpoint.CONFIG) {
            config.respond(request)
        } else {
            MockResponse()
        }
    }
}

private const val API_VERSION = "v2"
