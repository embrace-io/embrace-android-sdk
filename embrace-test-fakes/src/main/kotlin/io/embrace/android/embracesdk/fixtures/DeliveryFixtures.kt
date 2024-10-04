package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType

val fakeCachedSessionStoredTelemetryMetadata = StoredTelemetryMetadata(
    timestamp = DEFAULT_FAKE_CURRENT_TIME + 1000L,
    uuid = "30690ad1-6b87-4e08-b72c-7deca14451d8",
    envelopeType = SupportedEnvelopeType.SESSION,
    complete = false
)

val fakeSessionStoredTelemetryMetadata =
    fakeCachedSessionStoredTelemetryMetadata.copy(
        timestamp = fakeCachedSessionStoredTelemetryMetadata.timestamp + 1000L,
        complete = true
    )

val fakeSessionStoredTelemetryMetadata2 = StoredTelemetryMetadata(
    timestamp = DEFAULT_FAKE_CURRENT_TIME + 10_000L,
    uuid = "e6cfe01a-990e-4af7-b30e-f862947cef9b",
    envelopeType = SupportedEnvelopeType.SESSION
)

val fakeLogStoredTelemetryMetadata = StoredTelemetryMetadata(
    timestamp = DEFAULT_FAKE_CURRENT_TIME + 500L,
    uuid = "6bda3896-d4fd-42ce-89f6-47bec86f1c80",
    envelopeType = SupportedEnvelopeType.LOG
)
