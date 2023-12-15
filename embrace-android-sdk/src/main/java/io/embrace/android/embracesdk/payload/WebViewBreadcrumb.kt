package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Breadcrumb that represents the onPageStarted event for a WebView.
 */
internal class WebViewBreadcrumb(
    @SerializedName("u")
    val url: String,

    @SerializedName("st")
    internal val startTime: Long
) : Breadcrumb {
    override fun getStartTime(): Long = startTime
}
