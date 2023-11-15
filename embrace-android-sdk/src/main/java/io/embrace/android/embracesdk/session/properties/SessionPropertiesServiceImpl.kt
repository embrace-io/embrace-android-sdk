package io.embrace.android.embracesdk.session.properties

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService

internal class SessionPropertiesServiceImpl(
    private val ndkService: NdkService,
    private val sessionProperties: EmbraceSessionProperties,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : SessionPropertiesService {

    companion object {
        private const val TAG = "SessionPropertiesService"
    }

    override fun addProperty(key: String, value: String, permanent: Boolean): Boolean {
        logger.logDeveloper(TAG, "Add Property: $key - $value")
        val added = sessionProperties.add(key, value, permanent)
        if (added) {
            logger.logDeveloper(TAG, "Session properties updated")
            ndkService.onSessionPropertiesUpdate(sessionProperties.get())
        } else {
            logger.logDeveloper(TAG, "Cannot add property: $key")
        }
        return added
    }

    override fun removeProperty(key: String): Boolean {
        logger.logDeveloper(TAG, "Remove Property: $key")
        val removed = sessionProperties.remove(key)
        if (removed) {
            logger.logDeveloper(TAG, "Session properties updated")
            ndkService.onSessionPropertiesUpdate(sessionProperties.get())
        } else {
            logger.logDeveloper(TAG, "Cannot remove property: $key")
        }
        return removed
    }

    override fun getProperties(): Map<String, String> = sessionProperties.get()
}
