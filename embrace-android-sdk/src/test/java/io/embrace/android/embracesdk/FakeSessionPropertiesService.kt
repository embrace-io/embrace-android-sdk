package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.session.properties.SessionPropertiesService

internal class FakeSessionPropertiesService : SessionPropertiesService {

    var props: MutableMap<String, String> = mutableMapOf()

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
}
