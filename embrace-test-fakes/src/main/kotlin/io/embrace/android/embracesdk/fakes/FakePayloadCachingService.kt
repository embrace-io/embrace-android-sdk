package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload

class FakePayloadCachingService : PayloadCachingService {

    var responseAction: (intake: FakePayloadIntake<*>) -> String = { _ -> "" }
    var crashId: String? = null
    var cacheAttempts: MutableList<FakePayloadIntake<*>> = mutableListOf()
    var stopRequests: MutableList<String> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getCachedPayloads(): List<FakePayloadIntake<T>> {
        if (T::class != SessionPayload::class || T::class != LogPayload::class) {
            error("Unsupported type: ${T::class}")
        }
        return cacheAttempts.filter { it.envelope.data is T } as List<FakePayloadIntake<T>>
    }

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun <T> startCaching(
        envelopeProvider: () -> Envelope<T>,
        metadataProvider: () -> StoredTelemetryMetadata
    ): String {
        val intake = FakePayloadIntake(envelopeProvider(), metadataProvider())
        cacheAttempts.add(intake)
        return responseAction(intake)
    }

    override fun stopCaching(id: String) {
        stopRequests.add(id)
    }
}
