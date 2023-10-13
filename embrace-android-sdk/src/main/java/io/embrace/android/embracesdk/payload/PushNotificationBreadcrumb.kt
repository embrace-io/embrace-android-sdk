package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb

internal data class PushNotificationBreadcrumb(
    @SerializedName("ti")
    val title: String?,

    @SerializedName("bd")
    val body: String?,

    @SerializedName("tp")
    val from: String?,

    @SerializedName("id")
    internal val id: String?,

    @SerializedName("pt")
    val priority: Int?,

    @SerializedName("te")
    val type: String?,

    @SerializedName("ts")
    private val timestamp: Long

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
