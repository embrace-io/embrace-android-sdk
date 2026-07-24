package io.embrace.android.embracesdk.internal.network.http

import io.embrace.android.embracesdk.network.http.MutableHttpRequestInfo

/**
 * Mutable holder for the HTTP request info captured by network instrumentation. It is seeded with
 * the values captured for a request, passed through the registered [HttpRequestInfoModifier]s, and
 * then read back to generate telemetry. Mutating an instance does not affect the underlying HTTP
 * request that was executed.
 */
class MutableHttpRequestInfoImpl(
    override var httpMethod: String,
    override var url: String,
) : MutableHttpRequestInfo
