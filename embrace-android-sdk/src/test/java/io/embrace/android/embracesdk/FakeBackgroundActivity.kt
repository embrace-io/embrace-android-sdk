package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.BackgroundActivity
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.CustomBreadcrumb
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.UserInfo
import io.opentelemetry.api.trace.StatusCode

internal fun fakeBackgroundActivity(): BackgroundActivityMessage {
    val backgroundActivity =
        BackgroundActivity("fake-activity", 0, "", number = 1, messageType = "en", isColdStart = false)
    val userInfo = UserInfo("fake-user-id")
    val appInfo = AppInfo("fake-app-id")
    val deviceInfo = DeviceInfo("fake-manufacturer")
    val breadcrumbs = Breadcrumbs(
        customBreadcrumbs = listOf(CustomBreadcrumb("fake-breadcrumb", 1))
    )
    val spans = listOf(EmbraceSpanData("fake-span-id", "", "", "", 0, 0, StatusCode.OK))
    val perfInfo = PerformanceInfo(DiskUsage(1, 2))

    return BackgroundActivityMessage(
        backgroundActivity,
        userInfo,
        appInfo,
        deviceInfo,
        perfInfo,
        breadcrumbs,
        spans
    )
}
