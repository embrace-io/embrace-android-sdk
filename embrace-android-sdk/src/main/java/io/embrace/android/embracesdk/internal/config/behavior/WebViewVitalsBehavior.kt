package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface WebViewVitalsBehavior {
    public fun getMaxWebViewVitals(): Int
    public fun isWebViewVitalsEnabled(): Boolean
}
