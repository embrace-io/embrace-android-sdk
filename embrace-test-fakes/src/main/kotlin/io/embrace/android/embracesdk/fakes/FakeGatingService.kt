package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * An implementation of [GatingService] that does a pass-through to [EmbraceGatingService] but tracks the message that go through it
 */
public class FakeGatingService(configService: ConfigService = FakeConfigService()) :
    GatingService {
    public val envelopesFiltered: MutableList<Envelope<SessionPayload>> = mutableListOf()
    public val eventMessagesFiltered: MutableList<EventMessage> = mutableListOf()

    private val realGatingService = EmbraceGatingService(configService, FakeLogService(), EmbLoggerImpl())

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
