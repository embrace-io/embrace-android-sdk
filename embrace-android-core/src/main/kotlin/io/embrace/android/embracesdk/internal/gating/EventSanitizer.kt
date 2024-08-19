package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.LOG_PROPERTIES
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.SESSION_PROPERTIES
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventType

public class EventSanitizer(
    private val event: Event,
    private val enabledComponents: Set<String>
) : Sanitizable<Event> {

    override fun sanitize(): Event {
        var customPropertiesMap = event.customProperties
        var sessionPropertiesMap = event.sessionProperties

        if (isLogEvent()) {
            if (!shouldSendLogProperties()) {
                customPropertiesMap = null
            }
        }

        if (!shouldSendSessionProperties()) {
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
