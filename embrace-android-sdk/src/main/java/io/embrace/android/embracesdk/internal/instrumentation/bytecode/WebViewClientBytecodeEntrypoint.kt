package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @hide
 */
@InternalApi
@Keep
public object WebViewClientBytecodeEntrypoint {

    @JvmStatic
    @Keep
    public fun onPageStarted(url: String?,) {
        Embrace.getInstance().logWebView(url)
    }
}
