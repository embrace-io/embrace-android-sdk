package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage

internal fun fakeBackgroundActivityMessage(): SessionMessage {
    val backgroundActivity = fakeBackgroundActivity()

    return SessionMessage(
        backgroundActivity,
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
    0
)
