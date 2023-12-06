package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The data for the activity lifecycle breadcrumb. Note that this does not have the same structure
 * as the Breadcrumb.java interface.
 */
@JsonClass(generateAdapter = true)
internal data class ActivityLifecycleBreadcrumb(

    @Transient
    internal val activity: String? = null,

    @Json(name = "s")
    internal val state: ActivityLifecycleState,

    @Json(name = "st")
    internal val start: Long?,

    @Json(name = "b")
    internal var bundlePresent: Boolean? = false,

    @Json(name = "en")
    internal var end: Long? = -1,
)
