package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ActivityLifecycleData(
    @Json(name = "a")
    internal val activity: String?,

    @Json(name = "d")
    internal val data: List<ActivityLifecycleBreadcrumb>?
)
