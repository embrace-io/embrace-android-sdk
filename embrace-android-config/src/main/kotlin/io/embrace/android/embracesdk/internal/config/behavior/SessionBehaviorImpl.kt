package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
class SessionBehaviorImpl(private val remote: RemoteConfig?) : SessionBehavior {

    companion object {
        const val SESSION_PROPERTY_LIMIT: Int = 100
        const val SESSION_PROPERTY_MAX_LIMIT: Int = 200
    }

    override fun isSessionControlEnabled(): Boolean = remote?.sessionConfig?.isEnabled ?: false

    override fun getMaxSessionProperties(): Int = min(remote?.maxSessionProperties ?: SESSION_PROPERTY_LIMIT, SESSION_PROPERTY_MAX_LIMIT)
}
