package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.internal.StartupEventInfo

internal class FakeEventService : EventService {
    override fun startEvent(name: String) {
        TODO("Not yet implemented")
    }

    override fun startEvent(name: String, identifier: String?) {
        TODO("Not yet implemented")
    }

    override fun startEvent(name: String, identifier: String?, properties: Map<String, Any>?) {
        TODO("Not yet implemented")
    }

    override fun startEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?,
        startTime: Long?
    ) {
        TODO("Not yet implemented")
    }

    override fun endEvent(name: String) {
        TODO("Not yet implemented")
    }

    override fun endEvent(name: String, identifier: String?) {
        TODO("Not yet implemented")
    }

    override fun endEvent(name: String, properties: Map<String, Any>?) {
        TODO("Not yet implemented")
    }

    override fun endEvent(name: String, identifier: String?, properties: Map<String, Any>?) {
        TODO("Not yet implemented")
    }

    override fun findEventIdsForSession(startTime: Long, endTime: Long): List<String> {
        return emptyList()
    }

    override fun getActiveEventIds(): List<String>? {
        return null
    }

    override fun getStartupMomentInfo(): StartupEventInfo? {
        TODO("Not yet implemented")
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
