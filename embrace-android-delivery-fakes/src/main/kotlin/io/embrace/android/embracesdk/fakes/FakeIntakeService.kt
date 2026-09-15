package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.payload.Envelope
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class FakeIntakeService : IntakeService {

    var shutdownCount: Int = 0
    var intakeList: MutableList<FakePayloadIntake<*>> = mutableListOf()
    var cacheList: MutableList<FakePayloadIntake<*>> = mutableListOf()

    /**
     * Whether an intake is treated as stored. Set to false to simulate a payload that the intake
     * service dropped or failed to persist, in which case the onStored callback is not invoked.
     */
    var storeSucceeds: Boolean = true

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
    ): Future<*> {
        val dst = when (metadata.complete) {
            true -> intakeList
            false -> cacheList
        }
        dst.add(FakePayloadIntake(intake, metadata))
        if (storeSucceeds) {
            onStored?.invoke()
        }
        return fakeFuture
    }

    val fakeFuture = object : Future<Boolean> {
        override fun cancel(p0: Boolean): Boolean = false

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean = true

        override fun get() = true

        override fun get(p0: Long, p1: TimeUnit) = true
    }
}
