package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.session.orchestrator.TransitionType

class FakePayloadStore(
    private val deliveryService: FakeDeliveryService = FakeDeliveryService(),
) : PayloadStore {

    val storedSessionPayloads = mutableListOf<Pair<Envelope<SessionPayload>, TransitionType>>()
    val storedLogPayloads = mutableListOf<Pair<Envelope<LogPayload>, Boolean>>()
    val storedAttachments = mutableListOf<Envelope<ByteArray>>()
    val cachedSessionPayloads = mutableListOf<Envelope<SessionPayload>>()
    val cachedEmptyCrashPayloads = mutableListOf<Envelope<LogPayload>>()
    var crashCount: Int = 0

    override fun storeSessionPayload(
        envelope: Envelope<SessionPayload>,
        transitionType: TransitionType,
    ) {
        storedSessionPayloads.add(Pair(envelope, transitionType))
    }

    override fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>) {
        cachedSessionPayloads.add(envelope)
        deliveryService.sendSession(envelope, SessionSnapshotType.PERIODIC_CACHE)
    }

    override fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean) {
        storedLogPayloads.add(Pair(envelope, attemptImmediateRequest))
    }

    override fun storeAttachment(envelope: Envelope<ByteArray>) {
        storedAttachments.add(envelope)
    }

    override fun cacheEmptyCrashEnvelope(envelope: Envelope<LogPayload>) {
        cachedEmptyCrashPayloads.add(envelope)
    }

    override fun handleCrash(crashId: String) {
        crashCount++
    }
}
