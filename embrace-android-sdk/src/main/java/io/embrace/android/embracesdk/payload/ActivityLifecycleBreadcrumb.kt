package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * The data for the activity lifecycle breadcrumb. Note that this does not have the same structure
 * as the Breadcrumb.java interface.
 */
internal data class ActivityLifecycleBreadcrumb(

    @Transient
    internal val activity: String?,

    @SerializedName("s")
    internal val state: ActivityLifecycleState,

    @SerializedName("st")
    internal val start: Long?,

    @SerializedName("b")
    internal var bundlePresent: Boolean? = false,

    @SerializedName("en")
    internal var end: Long? = -1,
)
