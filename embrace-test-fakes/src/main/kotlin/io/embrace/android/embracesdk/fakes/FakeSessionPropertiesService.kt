package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService

class FakeSessionPropertiesService : SessionPropertiesService {

    var props: MutableMap<String, String> = mutableMapOf()
    var listeners: MutableList<(Map<String, String>) -> Unit> = mutableListOf()
    var cleanupAfterSessionEndCallCount = 0
    var prepareNewSessionCallCount = 0

    override fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean {
        props[originalKey] = originalValue
        return true
    }

    override fun removeProperty(originalKey: String): Boolean {
        props.remove(originalKey)
        return true
    }

    override fun getProperties(): Map<String, String> = props

    override fun cleanupAfterSessionEnd() {
        props.clear()
        cleanupAfterSessionEndCallCount++
    }

    override fun prepareForNewSession() {
        prepareNewSessionCallCount++
    }

    override fun addChangeListener(listener: (Map<String, String>) -> Unit) {
        listeners.add(listener)
    }
}
