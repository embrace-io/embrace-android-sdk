package io.embrace.android.embracesdk.internal.instrumentation.network

interface HttpPathOverrideRequest {
    fun getHeaderByName(name: String): String?

    fun getOverriddenURL(pathOverride: String): String

    fun getURLString(): String
}
