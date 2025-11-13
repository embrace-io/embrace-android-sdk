package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod

val fakeCompleteEmbraceNetworkRequest = EmbraceNetworkRequest.fromCompletedRequest(
    url = "https://embrace.io",
    httpMethod = HttpMethod.GET,
    startTime = 1000L,
    endTime = 2000L,
    bytesSent = 1,
    bytesReceived = 10,
    statusCode = 200,
)
