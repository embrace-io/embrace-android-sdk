package io.embrace.android.embracesdk.fakes

data class FakeNetworkRequest(
    val url: String,
    val httpMethod: String,
    val startTime: Long,
    val endTime: Long,
    val bytesSent: Long? = null,
    val bytesReceived: Long? = null,
    val statusCode: Int? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val traceId: String? = null,
    val w3cTraceparent: String? = null,
)

val fakeCompleteRequest = FakeNetworkRequest(
    url = "https://fakeurl.pizza/ur?stuff=true",
    httpMethod = "GET",
    startTime = 1000L,
    endTime = 2000L,
    bytesSent = 100L,
    bytesReceived = 200L,
    statusCode = 200,
    traceId = "fake-id",
    w3cTraceparent = "fake-traceparent",
)

val fakeIncompleteRequest = FakeNetworkRequest(
    url = "https://fakeurl.pizza/ur?stuff=true",
    httpMethod = "GET",
    startTime = 1000L,
    endTime = 2000L,
    errorType = "fakeType",
    errorMessage = "fake message",
    traceId = "fake-id",
    w3cTraceparent = "fake-traceparent",
)
