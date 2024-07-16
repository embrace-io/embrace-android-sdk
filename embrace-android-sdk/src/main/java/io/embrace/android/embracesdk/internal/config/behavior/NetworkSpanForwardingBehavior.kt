package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface NetworkSpanForwardingBehavior {
    public fun isNetworkSpanForwardingEnabled(): Boolean
}
