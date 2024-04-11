package io.embrace.android.embracesdk.session.properties

import io.embrace.android.embracesdk.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.ndk.NdkService

internal class EmbraceSessionPropertiesService(
    private val ndkService: NdkService,
    private val sessionProperties: EmbraceSessionProperties,
    private val sessionPropertiesDataSource: SessionPropertiesDataSource?
) : SessionPropertiesService {

    override fun addProperty(key: String, value: String, permanent: Boolean): Boolean {
        val added = sessionProperties.add(key, value, permanent)
        if (added) {
            sessionPropertiesDataSource?.apply {
                addProperty(key, value)
            }
            ndkService.onSessionPropertiesUpdate(sessionProperties.get())
        }
        return added
    }

    override fun removeProperty(key: String): Boolean {
        val removed = sessionProperties.remove(key)
        if (removed) {
            sessionPropertiesDataSource?.apply {
                removeProperty(key)
            }
            ndkService.onSessionPropertiesUpdate(sessionProperties.get())
        }
        return removed
    }

    override fun getProperties(): Map<String, String> = sessionProperties.get()
}
