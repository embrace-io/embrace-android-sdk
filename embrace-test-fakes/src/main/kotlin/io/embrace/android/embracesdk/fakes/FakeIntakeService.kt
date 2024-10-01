package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload

class FakeIntakeService : IntakeService {

    var shutdownCount: Int = 0
    var intakeList: MutableList<FakePayloadIntake<*>> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getIntakes(): List<FakePayloadIntake<T>> {
        if (T::class != SessionPayload::class && T::class != LogPayload::class) {
            error("Unsupported type: ${T::class}")
        }
        return intakeList.filter { it.envelope.data is T } as List<FakePayloadIntake<T>>
    }

    override fun shutdown() {
        shutdownCount++
    }

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata) {
        intakeList.add(FakePayloadIntake(intake, metadata))
    }
}
