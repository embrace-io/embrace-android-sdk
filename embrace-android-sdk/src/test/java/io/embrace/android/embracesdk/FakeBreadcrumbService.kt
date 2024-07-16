package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.internal.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb

internal class FakeBreadcrumbService : BreadcrumbService {

    val customCalls = mutableListOf<String?>()
    val startViewCalls = mutableListOf<String?>()
    val endViewCalls = mutableListOf<String?>()
    val tapCalls = mutableListOf<String?>()
    val rnActionCalls = mutableListOf<String?>()
    val webviewCalls = mutableListOf<String?>()

    override fun logView(screen: String?, timestamp: Long) {
    }

    override fun startView(name: String?): Boolean {
        startViewCalls.add(name)
        return false
    }

    override fun endView(name: String?): Boolean {
        endViewCalls.add(name)
        return false
    }

    override fun logTap(
        point: Pair<Float?, Float?>,
        element: String,
        timestamp: Long,
        type: TapBreadcrumb.TapBreadcrumbType
    ) {
        tapCalls.add(element)
    }

    override fun logCustom(message: String, timestamp: Long) {
        customCalls.add(message)
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
        rnActionCalls.add(name)
    }

    override fun logWebView(url: String?, startTime: Long) {
        webviewCalls.add(url)
    }

    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
    }
}
