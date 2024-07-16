package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.payload.EventMessage

internal class EventSanitizerFacade(
    private val eventMessage: EventMessage,
    private val components: Set<String>
) {

    fun getSanitizedMessage(): EventMessage {
        val sanitizedEvent = EventSanitizer(eventMessage.event, components).sanitize()
        val sanitizedUserInfo = UserInfoSanitizer(eventMessage.userInfo, components).sanitize()

        return eventMessage.copy(
            event = sanitizedEvent,
            userInfo = sanitizedUserInfo
        )
    }
}
