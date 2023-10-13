package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.EventMessage

internal class EventSanitizerFacade(
    private val eventMessage: EventMessage,
    private val components: Set<String>
) {

    fun getSanitizedMessage(): EventMessage {
        InternalStaticEmbraceLogger.logger.logDeveloper(
            "EventSanitizerFacade",
            "getSanitizedMessage"
        )
        val sanitizedEvent = EventSanitizer(eventMessage.event, components).sanitize()
        val sanitizedUserInfo = UserInfoSanitizer(eventMessage.userInfo, components).sanitize()
        val sanitizedPerformanceInfo =
            PerformanceInfoSanitizer(eventMessage.performanceInfo, components).sanitize()

        return eventMessage.copy(
            event = sanitizedEvent,
            userInfo = sanitizedUserInfo,
            performanceInfo = sanitizedPerformanceInfo
        )
    }
}
