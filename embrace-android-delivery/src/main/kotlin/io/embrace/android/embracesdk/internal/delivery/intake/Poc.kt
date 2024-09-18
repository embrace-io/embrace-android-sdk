package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Uuid

fun exampleUsage(
    intakeService: IntakeService,
    envelope: Envelope<SessionPayload>,
    clock: Clock
) {
    intakeService.take(
        envelope,
        StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = Uuid.getEmbUuid(),
            envelopeType = SupportedEnvelopeType.SESSION
        )
    )
}

internal interface SchedulingService { // placeholder
    fun checkPendingPayloads()
}
