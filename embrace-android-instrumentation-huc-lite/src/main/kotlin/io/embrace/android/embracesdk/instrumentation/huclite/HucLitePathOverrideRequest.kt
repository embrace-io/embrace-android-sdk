package io.embrace.android.embracesdk.instrumentation.huclite

import io.embrace.android.embracesdk.internal.instrumentation.network.HttpPathOverrideRequest
import java.net.MalformedURLException
import java.net.URL

internal class HucLitePathOverrideRequest(
    private val requestHeaderProvider: (name: String) -> String?,
    private val originalUrl: URL,
) : HttpPathOverrideRequest {
    override fun getHeaderByName(name: String): String? = requestHeaderProvider(name)

    override fun getOverriddenURL(pathOverride: String): String {
        return try {
            originalUrl.run {
                val newPath = if (query.isNullOrBlank()) {
                    pathOverride
                } else {
                    "$pathOverride?$query"
                }

                URL(protocol, host, port, newPath).toString()
            }
        } catch (_: MalformedURLException) {
            getURLString()
        }
    }

    override fun getURLString(): String = originalUrl.toString()
}
