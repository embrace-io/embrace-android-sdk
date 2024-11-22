package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload

fun fakeEmptyLogEnvelope() = Envelope(
    resource = EnvelopeResource(),
    metadata = EnvelopeMetadata(),
    version = "1.0.0",
    type = "logs",
    data = LogPayload()
)
