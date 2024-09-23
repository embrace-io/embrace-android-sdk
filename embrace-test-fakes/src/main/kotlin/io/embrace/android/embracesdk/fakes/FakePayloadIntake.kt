package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.payload.Envelope

data class FakePayloadIntake<T>(val envelope: Envelope<T>, val metadata: StoredTelemetryMetadata)
