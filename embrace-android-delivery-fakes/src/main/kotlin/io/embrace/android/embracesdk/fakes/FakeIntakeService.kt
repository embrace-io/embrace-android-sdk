package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeResult
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.payload.Envelope
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

class FakeIntakeService : IntakeService {

    var shutdownCount: Int = 0
    var intakeList: MutableList<FakePayloadIntake<*>> = mutableListOf()
    var cacheList: MutableList<FakePayloadIntake<*>> = mutableListOf()

    /**
     * Each intake takes the first item off this list as its result. An empty list means intakes are stored.
     */
    val pendingResults: ArrayDeque<IntakeResult> = ArrayDeque()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getIntakes(complete: Boolean = true): List<FakePayloadIntake<T>> {
        val dst = when (complete) {
            true -> intakeList
            false -> cacheList
        }
        return dst.filter { it.envelope.data is T } as List<FakePayloadIntake<T>>
    }

    override fun shutdown() {
        shutdownCount++
    }

    override fun take(
        intake: Envelope<*>,
        metadata: StoredTelemetryMetadata,
        staleEntry: StoredTelemetryMetadata?,
        onStored: (() -> Unit)?,
    ): Future<IntakeResult> {
        val dst = when (metadata.complete) {
            true -> intakeList
            false -> cacheList
        }
        dst.add(FakePayloadIntake(intake, metadata))
        val result = pendingResults.removeFirstOrNull() ?: IntakeResult.STORED
        if (result == IntakeResult.STORED) {
            onStored?.invoke()
        }
        return FutureTask { result }.apply { run() }
    }
}
