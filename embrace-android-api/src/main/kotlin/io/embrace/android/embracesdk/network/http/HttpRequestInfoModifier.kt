package io.embrace.android.embracesdk.network.http

public fun interface HttpRequestInfoModifier {
    public fun modifyHttpRequestInfo(info: MutableHttpRequestInfo)
}
