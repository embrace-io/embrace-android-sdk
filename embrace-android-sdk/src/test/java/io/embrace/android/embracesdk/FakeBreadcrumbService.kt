package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb

internal class FakeBreadcrumbService : BreadcrumbService {

    val logViewCalls = mutableListOf<String?>()
    val pushNotifications = mutableListOf<PushNotificationBreadcrumb>()
    var flushCount: Int = 0

    override fun getBreadcrumbs(): Breadcrumbs {
        return Breadcrumbs()
    }

    override fun flushBreadcrumbs(): Breadcrumbs {
        flushCount++
        return Breadcrumbs()
    }

    override fun logView(screen: String?, timestamp: Long) {
    }

    override fun forceLogView(screen: String?, timestamp: Long) {
        logViewCalls.add(screen)
    }

    override fun startView(name: String?): Boolean {
        return false
    }

    override fun endView(name: String?): Boolean {
        return false
    }

    override fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumb.TapBreadcrumbType
    ) {
    }

    override fun logCustom(message: String, timestamp: Long) {
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
    }

    override fun logWebView(url: String?, startTime: Long) {
    }

    var viewBreadcrumbScreenName: String? = null
    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
        pushNotifications.add(
            PushNotificationBreadcrumb(
                title,
                body,
                topic,
                id,
                notificationPriority,
                type.type,
                0L
            )
        )
    }
}
