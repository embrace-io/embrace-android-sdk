package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOG_PROPERTIES
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_PROPERTIES
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Event

internal class EventSanitizer(
    private val event: Event,
    private val enabledComponents: Set<String>
) : Sanitizable<Event> {

    override fun sanitize(): Event {
        InternalStaticEmbraceLogger.logger.logDeveloper("EventSanitizer", "sanitize")
        var customPropertiesMap = event.customProperties
        var sessionPropertiesMap = event.sessionProperties

        InternalStaticEmbraceLogger.logger.logDeveloper(
            "EventSanitizer",
            "isLogEvent: " + isLogEvent()
        )
        if (isLogEvent()) {
            if (!shouldSendLogProperties()) {
                InternalStaticEmbraceLogger.logger.logDeveloper(
                    "EventSanitizer",
                    "not shouldSendLogProperties"
                )
                customPropertiesMap = null
            }
        }

        if (!shouldSendSessionProperties()) {
            InternalStaticEmbraceLogger.logger.logDeveloper(
                "EventSanitizer",
                "not shouldSendSessionProperties"
            )
            sessionPropertiesMap = null
        }

        return event.copy(
            customProperties = customPropertiesMap,
            sessionProperties = sessionPropertiesMap
        )
    }

    private fun isLogEvent() =
        event.type == EventType.ERROR_LOG ||
            event.type == EventType.WARNING_LOG ||
            event.type == EventType.INFO_LOG

    private fun shouldSendLogProperties() =
        enabledComponents.contains(LOG_PROPERTIES)

    private fun shouldSendSessionProperties() =
        enabledComponents.contains(SESSION_PROPERTIES)
}
