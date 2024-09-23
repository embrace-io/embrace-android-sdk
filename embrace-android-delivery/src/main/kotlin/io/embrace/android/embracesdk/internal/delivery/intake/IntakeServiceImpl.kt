package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.payload.Envelope

internal class IntakeServiceImpl(
    @Suppress("unused") private val schedulingService: SchedulingService
) : IntakeService {

    override fun handleCrash(crashId: String) {
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
    }
}
