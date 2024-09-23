package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.payload.Envelope

class FakeIntakeService : IntakeService {

    var crashId: String? = null
    var intakeList: MutableList<Pair<Envelope<*>, StoredTelemetryMetadata>> =
        mutableListOf()

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        intakeList.add(Pair(intake, metadata))
    }
}
