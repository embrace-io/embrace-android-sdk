package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.JsonClass

public class PushNotificationBreadcrumb {

    @JsonClass(generateAdapter = false)
    public enum class NotificationType(public val type: String) {
        NOTIFICATION("notif"),
        DATA("data"),

        // this is a notification + data
        NOTIFICATION_AND_DATA("notif-data"),
        UNKNOWN("unknown");

        public companion object Builder {
            public fun notificationTypeFor(hasData: Boolean, hasNotification: Boolean): NotificationType {
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
