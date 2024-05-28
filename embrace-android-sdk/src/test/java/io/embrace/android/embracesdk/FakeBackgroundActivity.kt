package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.opentelemetry.api.trace.StatusCode

internal fun fakeBackgroundActivityMessage(): SessionMessage {
    val backgroundActivity = fakeBackgroundActivity()
    val spans = listOf(EmbraceSpanData("fake-span-id", "", "", "", 0, 0, StatusCode.OK))
    val perfInfo = PerformanceInfo(DiskUsage(1, 2))

    return SessionMessage(
        backgroundActivity,
        perfInfo,
        spans,
        metadata = EnvelopeMetadata(
            userId = "fake-user-id",
        ),
        resource = EnvelopeResource(
            appVersion = "fake-app-id",
            deviceManufacturer = "fake-manufacturer"
        )
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
