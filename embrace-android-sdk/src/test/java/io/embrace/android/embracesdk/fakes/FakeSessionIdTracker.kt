package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.ndk.NdkService

internal class FakeSessionIdTracker : SessionIdTracker {

    var sessionId: String? = null

    override var ndkService: NdkService? = null

    override fun getActiveSessionId(): String? {
        return sessionId
    }

    override fun setActiveSessionId(sessionId: String?, isSession: Boolean) {
        this.sessionId = sessionId
    }
}
