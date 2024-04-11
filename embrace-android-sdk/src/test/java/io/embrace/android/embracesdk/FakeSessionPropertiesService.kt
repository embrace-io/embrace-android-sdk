package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class FakeSessionPropertiesService : SessionPropertiesService {
    override fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeProperty(originalKey: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getProperties(): Map<String, String> = emptyMap()

    override fun populateCurrentSession(): Boolean = true
}
