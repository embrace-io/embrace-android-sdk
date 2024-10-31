package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * An implementation of [GatingService] that does a pass-through to a delegate & tracks the message
 * that goes through it
 */
class FakeGatingService(private val impl: GatingService? = null) : GatingService {

    val envelopesFiltered: MutableList<Envelope<SessionPayload>> = mutableListOf()

    override fun gateSessionEnvelope(
        hasCrash: Boolean,
        envelope: Envelope<SessionPayload>,
    ): Envelope<SessionPayload> {
        val filteredMessage = impl?.gateSessionEnvelope(hasCrash, envelope) ?: envelope
        envelopesFiltered.add(filteredMessage)
        return envelope
    }
}
