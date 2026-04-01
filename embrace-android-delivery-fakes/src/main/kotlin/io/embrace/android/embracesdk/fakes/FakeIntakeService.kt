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

    override fun take(intake: Envelope<*>, metadata: StoredTelemetryMetadata, staleEntry: StoredTelemetryMetadata?): Future<*> {
        val dst = when (metadata.complete) {
            true -> intakeList
            false -> cacheList
        }
        dst.add(FakePayloadIntake(intake, metadata))
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
