package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.JsonClass

class PushNotificationBreadcrumb {

    @JsonClass(generateAdapter = false)
    enum class NotificationType(val type: String) {
        NOTIFICATION("notif"),
        DATA("data"),

        // this is a notification + data
        NOTIFICATION_AND_DATA("notif-data"),
        UNKNOWN("unknown");

        companion object Builder {
            fun notificationTypeFor(hasData: Boolean, hasNotification: Boolean): NotificationType {
                return when {
                    hasData && hasNotification -> NOTIFICATION_AND_DATA
                    hasData && !hasNotification -> DATA
                    !hasData && hasNotification -> NOTIFICATION
                    else -> UNKNOWN
                }
            }
        }
    }
}
