package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.assertions.getStartTime
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

val testSessionEnvelope: Envelope<SessionPayload> = fakeSessionEnvelope(
    sessionId = "80DCEC19B24B434599B04C237970B785",
    startMs = 1681972471807L
)

val testSessionEnvelopeOneMinuteLater: Envelope<SessionPayload> = fakeSessionEnvelope(
    startMs = testSessionEnvelope.getStartTime() + 60000L
)
