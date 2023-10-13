package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class ActivityLifecycleData(
    @SerializedName("a")
    internal val activity: String?,

    @SerializedName("d")
    internal val data: List<ActivityLifecycleBreadcrumb>?
)
