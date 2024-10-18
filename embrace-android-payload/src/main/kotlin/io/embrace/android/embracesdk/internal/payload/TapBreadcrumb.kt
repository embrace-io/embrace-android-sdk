package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Breadcrumbs that represent tap events.
 */
class TapBreadcrumb {

    @JsonClass(generateAdapter = false)
    enum class TapBreadcrumbType(val value: String) {
        @Json(name = "s")
        TAP("tap"),

        @Json(name = "l")
        LONG_PRESS("long_press")
    }
}
