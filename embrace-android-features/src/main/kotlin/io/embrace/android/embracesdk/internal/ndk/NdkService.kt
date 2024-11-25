package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

interface NdkService {
    fun updateSessionId(newSessionId: String)

    fun onSessionPropertiesUpdate(properties: Map<String, String>)

    fun onUserInfoUpdate()

    fun initializeService(sessionIdTracker: SessionIdTracker)
}
