package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.internal.network.http.HttpPathOverrideRequest
import okhttp3.Request

internal class EmbraceOkHttp3PathOverrideRequest(private val request: Request) : HttpPathOverrideRequest {
    override fun getHeaderByName(name: String): String? {
        return request.header(name)
    }

    override fun getOverriddenURL(pathOverride: String): String {
        return request.url.newBuilder().encodedPath(pathOverride).build().toString()
    }

    override fun getURLString(): String {
        return request.url.toString()
    }
}
