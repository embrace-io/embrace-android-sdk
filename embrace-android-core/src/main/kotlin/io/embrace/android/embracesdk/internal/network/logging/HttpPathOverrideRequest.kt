package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
interface HttpPathOverrideRequest {
    fun getHeaderByName(name: String): String?

    fun getOverriddenURL(pathOverride: String): String

    fun getURLString(): String
}
