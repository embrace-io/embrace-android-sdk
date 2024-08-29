package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * An implementation of [GatingService] that does a pass-through to a delegate & tracks the message
 * that goes through it
 */
public class FakeGatingService(private val impl: GatingService? = null) : GatingService {

    public val envelopesFiltered: MutableList<Envelope<SessionPayload>> = mutableListOf()
    public val eventMessagesFiltered: MutableList<EventMessage> = mutableListOf()

    override fun gateSessionEnvelope(
        hasCrash: Boolean,
        envelope: Envelope<SessionPayload>
    ): Envelope<SessionPayload> {
        val filteredMessage = impl?.gateSessionEnvelope(hasCrash, envelope) ?: envelope
        envelopesFiltered.add(filteredMessage)
        return envelope
    }

    override fun gateEventMessage(eventMessage: EventMessage): EventMessage {
        val filteredMessage = impl?.gateEventMessage(eventMessage) ?: eventMessage
        eventMessagesFiltered.add(eventMessage)
        return filteredMessage
    }
}
