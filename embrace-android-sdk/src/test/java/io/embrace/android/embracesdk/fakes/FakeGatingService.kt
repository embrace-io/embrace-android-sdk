package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.EventMessage

/**
 * An implementation of [GatingService] that does a pass-through to [EmbraceGatingService] but tracks the message that go through it
 */
internal class FakeGatingService(configService: ConfigService = FakeConfigService()) :
    GatingService {
    val envelopesFiltered = mutableListOf<Envelope<SessionPayload>>()
    val eventMessagesFiltered = mutableListOf<EventMessage>()

    private val realGatingService = EmbraceGatingService(configService, FakeLogMessageService(), EmbLoggerImpl())

    override fun gateSessionEnvelope(
        hasCrash: Boolean,
        envelope: Envelope<SessionPayload>
    ): Envelope<SessionPayload> {
        val filteredMessage = realGatingService.gateSessionEnvelope(hasCrash, envelope)
        envelopesFiltered.add(filteredMessage)
        return envelope
    }

    override fun gateEventMessage(eventMessage: EventMessage): EventMessage {
        val filteredMessage = realGatingService.gateEventMessage(eventMessage)
        eventMessagesFiltered.add(eventMessage)
        return filteredMessage
    }
}
