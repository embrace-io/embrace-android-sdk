package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Contains lists of [ViewBreadcrumb], [TapBreadcrumb], [CustomBreadcrumb],
 * and [WebViewBreadcrumb] within a particular time window, created by the
 * [EmbraceBreadcrumbService].
 *
 * Breadcrumbs are used to track user journeys throughout the apps, such as transitions between
 * screens or taps on particular UI elements. A developer can create a [CustomBreadcrumb] if
 * they would like to label some particular event or interaction within their app on the timeline.
 */
internal data class Breadcrumbs(

    /**
     * List of breadcrumbs which relate to views.
     */
    @SerializedName("vb")
    val viewBreadcrumbs: List<ViewBreadcrumb>? = null,

    /**
     * List of breadcrumbs which relate to screen taps.
     */
    @SerializedName("tb")
    val tapBreadcrumbs: List<TapBreadcrumb>? = null,

    /**
     * List of custom breadcrumbs defined by the developer.
     */
    @SerializedName("cb")
    val customBreadcrumbs: List<CustomBreadcrumb>? = null,

    /**
     * List of webview breadcrumbs.
     */
    @SerializedName("wv")
    val webViewBreadcrumbs: List<WebViewBreadcrumb>? = null,

    /**
     * List of fragment (custom view) breadcrumbs.
     */
    @SerializedName("cv")
    val fragmentBreadcrumbs: List<FragmentBreadcrumb>? = null,

    /**
     * List of RN Action breadcrumbs.
     */
    @SerializedName("rna")
    val rnActionBreadcrumbs: List<RnActionBreadcrumb>? = null,

    /**
     * List of captured push notifications
     */
    @SerializedName("pn")
    val pushNotifications: List<PushNotificationBreadcrumb>? = null,
)
