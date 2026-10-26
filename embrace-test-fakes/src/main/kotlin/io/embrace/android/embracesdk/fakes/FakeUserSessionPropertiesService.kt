package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.session.PropertyScope
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService

class FakeUserSessionPropertiesService : UserSessionPropertiesService {

    var props: MutableMap<String, String> = mutableMapOf()
    var listeners: MutableList<(Map<String, String>) -> Unit> = mutableListOf()
    var cleanupAfterUserSessionEndCallCount = 0
    var prepareNewSessionPartCallCount = 0

    override fun addProperty(originalKey: String, originalValue: String, scope: PropertyScope): Boolean {
        props[originalKey] = originalValue
        return true
    }

    override fun removeProperty(originalKey: String): Boolean {
        props.remove(originalKey)
        return true
    }

    override fun getProperties(): Map<String, String> = props

    override fun cleanupAfterUserSessionEnd() {
        props.clear()
        cleanupAfterUserSessionEndCallCount++
    }

    override fun prepareForNewSessionPart() {
        prepareNewSessionPartCallCount++
    }

    override fun addChangeListener(listener: (Map<String, String>) -> Unit) {
        listeners.add(listener)
    }
}
