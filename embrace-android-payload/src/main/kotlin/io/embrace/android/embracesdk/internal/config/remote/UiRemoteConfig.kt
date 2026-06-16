package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to the user interface of the app.
 */
@Serializable
data class UiRemoteConfig(

    /**
     * The maximum number of custom breadcrumbs to send per session.
     */
    val breadcrumbs: Int? = null,
    val taps: Int? = null,
    @SerialName("web_views")
    val webViews: Int? = null,
    val fragments: Int? = null,
)
