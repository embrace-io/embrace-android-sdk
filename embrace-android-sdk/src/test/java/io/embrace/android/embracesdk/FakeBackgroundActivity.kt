package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.opentelemetry.api.trace.StatusCode

internal fun fakeBackgroundActivityMessage(): SessionMessage {
    val backgroundActivity = fakeBackgroundActivity()
    val userInfo = UserInfo("fake-user-id")
    val appInfo = AppInfo("fake-app-id")
    val deviceInfo = DeviceInfo("fake-manufacturer")
    val breadcrumbs = Breadcrumbs()
    val spans = listOf(EmbraceSpanData("fake-span-id", "", "", "", 0, 0, StatusCode.OK))
    val perfInfo = PerformanceInfo(DiskUsage(1, 2))

    return SessionMessage(
        backgroundActivity,
        userInfo,
        appInfo,
        deviceInfo,
        perfInfo,
        breadcrumbs,
        spans
    )
}

internal fun fakeBackgroundActivity() = Session(
    "fake-activity",
    0,
    appState = "background",
    number = 1,
    messageType = "en",
    isColdStart = false,
    startType = Session.LifeEventType.STATE
)
