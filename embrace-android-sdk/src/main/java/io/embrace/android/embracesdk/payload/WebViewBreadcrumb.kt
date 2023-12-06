package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

/**
 * Breadcrumb that represents the onPageStarted event for a WebView.
 */
@JsonClass(generateAdapter = true)
internal class WebViewBreadcrumb(
    @Json(name = "u")
    val url: String,

    @Json(name = "st")
    internal val startTime: Long
) : Breadcrumb {
    override fun getStartTime(): Long = startTime
}
