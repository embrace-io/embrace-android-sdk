package io.embrace.android.embracesdk

import okhttp3.mockwebserver.RecordedRequest

internal class RequestValidator(
    val endpoint: EmbraceEndpoint,
    val validate: (response: RecordedRequest) -> Unit
)
