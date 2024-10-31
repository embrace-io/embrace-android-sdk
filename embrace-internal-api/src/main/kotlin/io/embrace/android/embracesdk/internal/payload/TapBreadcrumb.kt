package io.embrace.android.embracesdk.internal.payload

/**
 * Breadcrumbs that represent tap events.
 */
class TapBreadcrumb {

    enum class TapBreadcrumbType(val value: String) {
        TAP("tap"),
        LONG_PRESS("long_press")
    }
}
