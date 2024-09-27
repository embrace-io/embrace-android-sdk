package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType

val fakeSessionStoredTelemetryMetadata = StoredTelemetryMetadata(
    timestamp = DEFAULT_FAKE_CURRENT_TIME + 2000L,
    uuid = "30690ad1-6b87-4e08-b72c-7deca14451d8",
    envelopeType = SupportedEnvelopeType.SESSION
)

val fakeSessionStoredTelemetryMetadata2 = StoredTelemetryMetadata(
    timestamp = DEFAULT_FAKE_CURRENT_TIME + 10_000L,
    uuid = "30690ad1-6b87-4e08-b72c-7deca14451d8",
    envelopeType = SupportedEnvelopeType.SESSION
)

val fakeLogStoredTelemetryMetadata = StoredTelemetryMetadata(
    timestamp = DEFAULT_FAKE_CURRENT_TIME + 500L,
    uuid = "6bda3896-d4fd-42ce-89f6-47bec86f1c80",
    envelopeType = SupportedEnvelopeType.LOG
)
