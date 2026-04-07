package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.TransitionType

class FakePayloadStore : PayloadStore {

    val storedSessionPartPayloads = mutableListOf<Pair<Envelope<SessionPartPayload>, TransitionType>>()
    val storedLogPayloads = mutableListOf<Pair<Envelope<LogPayload>, Boolean>>()
    val storedAttachments = mutableListOf<Envelope<Pair<String, ByteArray>>>()
    val cachedSessionPartPayloads = mutableListOf<Envelope<SessionPartPayload>>()
    val cachedEmptyCrashPayloads = mutableListOf<Envelope<LogPayload>>()
    var crashCount: Int = 0

    override fun storeSessionPartPayload(
        envelope: Envelope<SessionPartPayload>,
        transitionType: TransitionType,
    ) {
        storedSessionPartPayloads.add(Pair(envelope, transitionType))
    }

    override fun cacheSessionPartSnapshot(envelope: Envelope<SessionPartPayload>) {
        cachedSessionPartPayloads.add(envelope)
    }

    override fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean) {
        storedLogPayloads.add(Pair(envelope, attemptImmediateRequest))
    }

    override fun storeAttachment(envelope: Envelope<Pair<String, ByteArray>>) {
        storedAttachments.add(envelope)
    }

    override fun cacheEmptyCrashEnvelope(envelope: Envelope<LogPayload>) {
        cachedEmptyCrashPayloads.add(envelope)
    }

    override fun handleCrash(crashId: String) {
        crashCount++
    }
}
