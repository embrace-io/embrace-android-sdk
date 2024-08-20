package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.event.EventService
import io.embrace.android.embracesdk.internal.event.StartupEventInfo

public class FakeEventService : EventService {

    public class EventParams(
        public val name: String,
        public val identifier: String? = null,
        public val properties: Map<String, Any>? = null,
        public val startTime: Long? = null
    )

    public val startedEvents: MutableList<EventParams> = mutableListOf()
    public val endedEvents: MutableList<EventParams> = mutableListOf()

    override fun startEvent(name: String) {
        startedEvents.add(EventParams(name))
    }

    override fun startEvent(name: String, identifier: String?) {
        startedEvents.add(EventParams(name, identifier))
    }

    override fun startEvent(name: String, identifier: String?, properties: Map<String, Any>?) {
        startedEvents.add(EventParams(name, identifier, properties))
    }

    override fun startEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?,
        startTime: Long?
    ) {
        startedEvents.add(EventParams(name, identifier, properties, startTime))
    }

    override fun endEvent(name: String) {
        endedEvents.add(EventParams(name))
    }

    override fun endEvent(name: String, identifier: String?) {
        endedEvents.add(EventParams(name, identifier))
    }

    override fun endEvent(name: String, properties: Map<String, Any>?) {
        endedEvents.add(EventParams(name, properties = properties))
    }

    override fun endEvent(name: String, identifier: String?, properties: Map<String, Any>?) {
        endedEvents.add(EventParams(name, identifier, properties))
    }

    override fun findEventIdsForSession(): List<String> {
        return emptyList()
    }

    override fun getActiveEventIds(): List<String>? {
        return null
    }

    override fun getStartupMomentInfo(): StartupEventInfo? {
        return StartupEventInfo(0, 0)
    }

    override fun sendStartupMoment() {
        TODO("Not yet implemented")
    }

    override fun setProcessStartedByNotification() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
