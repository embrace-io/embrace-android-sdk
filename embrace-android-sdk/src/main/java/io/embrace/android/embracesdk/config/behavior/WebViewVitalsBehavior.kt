package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface WebViewVitalsBehavior {
    public fun getMaxWebViewVitals(): Int
    public fun isWebViewVitalsEnabled(): Boolean
}
