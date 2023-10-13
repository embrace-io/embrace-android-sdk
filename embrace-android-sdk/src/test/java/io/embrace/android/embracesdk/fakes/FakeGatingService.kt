package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage

/**
 * An implementation of [GatingService] that does a pass-through to [EmbraceGatingService] but tracks the message that go through it
 */
internal class FakeGatingService(configService: ConfigService = FakeConfigService()) :
    GatingService {
    val sessionMessagesFiltered = mutableListOf<SessionMessage>()
    val eventMessagesFiltered = mutableListOf<EventMessage>()

    private val realGatingService = EmbraceGatingService(configService)

    override fun gateSessionMessage(sessionMessage: SessionMessage): SessionMessage {
        val filteredMessage = realGatingService.gateSessionMessage(sessionMessage)
        sessionMessagesFiltered.add(sessionMessage)
        return filteredMessage
    }

    override fun gateEventMessage(eventMessage: EventMessage): EventMessage {
        val filteredMessage = realGatingService.gateEventMessage(eventMessage)
        eventMessagesFiltered.add(eventMessage)
        return filteredMessage
    }
}
