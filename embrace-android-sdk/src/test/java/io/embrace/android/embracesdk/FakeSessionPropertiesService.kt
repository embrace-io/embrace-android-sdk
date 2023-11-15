package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class FakeSessionPropertiesService : SessionPropertiesService {
    override fun addProperty(key: String, value: String, permanent: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeProperty(key: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getProperties(): Map<String, String> = emptyMap()
}
