package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

@JsonClass(generateAdapter = true)
internal data class PushNotificationBreadcrumb(
    @Json(name = "ti")
    val title: String?,

    @Json(name = "bd")
    val body: String?,

    @Json(name = "tp")
    val from: String?,

    @Json(name = "id")
    internal val id: String?,

    @Json(name = "pt")
    val priority: Int?,

    @Json(name = "te")
    val type: String?,

    @Json(name = "ts")
    internal val timestamp: Long

) : Breadcrumb {

    internal enum class NotificationType(val type: String) {
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

    override fun getStartTime(): Long {
        return timestamp
    }
}
