package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.payload.Envelope

class NoopIntakeService : IntakeService {
    override fun shutdown() {
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
    }
}
