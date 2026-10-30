package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.network.parent.NoopNetworkSpanParentToken
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.SpanContext

class FakeNetworkRequestApi(
    private val traceparent: String? = null,
) : NetworkRequestApi {
    val requests = mutableListOf<EmbraceNetworkRequest>()
    val httpRequestInfoModifiers = mutableListOf<HttpRequestInfoModifier>()

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        requests.add(networkRequest)
    }

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        httpRequestInfoModifiers.add(modifier)
    }

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        httpRequestInfoModifiers.remove(modifier)
    }

    override fun setNetworkSpanParent(span: EmbraceSpan): NetworkSpanParentToken = NoopNetworkSpanParentToken

    @OptIn(ExperimentalApi::class)
    override fun setNetworkSpanParent(spanContext: SpanContext): NetworkSpanParentToken = NoopNetworkSpanParentToken

    override fun clearNetworkSpanParent(token: NetworkSpanParentToken) {}

    override fun setNetworkSpanParentProvider(provider: NetworkSpanParentProvider) {}

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String? = traceparent
}
