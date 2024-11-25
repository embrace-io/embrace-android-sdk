package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

class FakeNdkService : NdkService {
    val propUpdates: MutableList<Map<String, String>> = mutableListOf()
    var sessionId: String? = null
    var userUpdateCount: Int = 0

    override fun initializeService(sessionIdTracker: SessionIdTracker) {
    }

    override fun updateSessionId(newSessionId: String) {
        sessionId = newSessionId
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        propUpdates.add(properties)
    }

    override fun onUserInfoUpdate() {
        userUpdateCount++
    }
}
