package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configuration values relating to the user interface of the app.
 */
internal data class UiRemoteConfig(

    /**
     * The maximum number of custom breadcrumbs to send per session.
     */
    val breadcrumbs: Int? = null,
    val taps: Int? = null,
    val views: Int? = null,
    @SerializedName("web_views")
    val webViews: Int? = null,
    val fragments: Int? = null
)
