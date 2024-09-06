package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration values relating to the user interface of the app.
 */
@JsonClass(generateAdapter = true)
data class UiRemoteConfig(

    /**
     * The maximum number of custom breadcrumbs to send per session.
     */
    val breadcrumbs: Int? = null,
    val taps: Int? = null,
    val views: Int? = null,
    @Json(name = "web_views")
    val webViews: Int? = null,
    val fragments: Int? = null
)
