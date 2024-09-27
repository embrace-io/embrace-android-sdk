package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.TransitionType

class FakePayloadStore : PayloadStore {

    val storedSessionPayloads = mutableListOf<Pair<Envelope<SessionPayload>, TransitionType>>()
    val storedLogPayloads = mutableListOf<Pair<Envelope<LogPayload>, Boolean>>()
    val cachedSessionPayloads = mutableListOf<Envelope<SessionPayload>>()
    var crashCount: Int = 0

    override fun storeSessionPayload(
        envelope: Envelope<SessionPayload>,
        transitionType: TransitionType
    ) {
        storedSessionPayloads.add(Pair(envelope, transitionType))
    }

    override fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>) {
        cachedSessionPayloads.add(envelope)
    }

    override fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean) {
        storedLogPayloads.add(Pair(envelope, attemptImmediateRequest))
    }

    override fun onCrash() {
        crashCount++
    }
}
