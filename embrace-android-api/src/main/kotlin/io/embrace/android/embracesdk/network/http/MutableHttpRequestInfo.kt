package io.embrace.android.embracesdk.network.http

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Mutable `HttpRequestInfo` to be used for altering the reported HTTP request data before it is reported as telemetry.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalApi::class)
public interface MutableHttpRequestInfo : HttpRequestInfo {
    override var httpMethod: String
    override var url: String
}
