package io.embrace.android.embracesdk.network.http

/**
 * Mutable `HttpRequestInfo` to be used for altering the reported HTTP request data before it is reported as telemetry.
 */
public interface MutableHttpRequestInfo : HttpRequestInfo {
    override var httpMethod: String
    override var url: String
}
