package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService

public class FakeSessionPropertiesService : SessionPropertiesService {

    public var props: MutableMap<String, String> = mutableMapOf()

    override fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean {
        props[originalKey] = originalValue
        return true
    }

    override fun removeProperty(originalKey: String): Boolean {
        props.remove(originalKey)
        return true
    }

    override fun getProperties(): Map<String, String> = props

    override fun populateCurrentSession(): Boolean = true

    override fun clearTemporary() {
        props.clear()
    }

    override fun addChangeListener(listener: (Map<String, String>) -> Unit) {
    }
}
