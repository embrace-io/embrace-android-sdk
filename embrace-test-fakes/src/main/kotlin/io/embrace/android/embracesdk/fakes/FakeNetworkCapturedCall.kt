package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

public fun fakeNetworkCapturedCall(): NetworkCapturedCall {
    return NetworkCapturedCall(
        duration = 100,
        endTime = 1713453000,
        httpMethod = "GET",
        matchedUrl = "httpbin.*",
        requestBody = "body",
        requestBodySize = 10,
        networkId = "id",
        requestQuery = "query",
        requestQueryHeaders = mapOf("query-header" to "value"),
        requestSize = 5,
        responseBody = "response",
        responseBodySize = 8,
        responseHeaders = mapOf("response-header" to "value"),
        responseSize = 300,
        responseStatus = 200,
        sessionId = "fake-session-id",
        startTime = 1713452000,
        url = "https://httpbin.org/get",
        errorMessage = "",
        encryptedPayload = "encrypted-payload"
    )
}
