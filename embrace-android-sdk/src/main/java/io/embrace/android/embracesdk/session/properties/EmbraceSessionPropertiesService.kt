package io.embrace.android.embracesdk.session.properties

import io.embrace.android.embracesdk.ndk.NdkService

internal class EmbraceSessionPropertiesService(
    private val ndkService: NdkService,
    private val sessionProperties: EmbraceSessionProperties
) : SessionPropertiesService {

    override fun addProperty(key: String, value: String, permanent: Boolean): Boolean {
        val added = sessionProperties.add(key, value, permanent)
        if (added) {
            ndkService.onSessionPropertiesUpdate(sessionProperties.get())
        }
        return added
    }

    override fun removeProperty(key: String): Boolean {
        val removed = sessionProperties.remove(key)
        if (removed) {
            ndkService.onSessionPropertiesUpdate(sessionProperties.get())
        }
        return removed
    }

    override fun getProperties(): Map<String, String> = sessionProperties.get()
}
