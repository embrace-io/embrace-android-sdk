package io.embrace.android.embracesdk.internal.payload

/**
 * Breadcrumbs that represent tap events.
 */
public class TapBreadcrumb {

    public enum class TapBreadcrumbType(public val value: String) {
        TAP("tap"),
        LONG_PRESS("long_press")
    }
}
