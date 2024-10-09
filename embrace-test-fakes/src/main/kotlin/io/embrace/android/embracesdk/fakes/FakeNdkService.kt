package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

class FakeNdkService : NdkService {
    val propUpdates: MutableList<Map<String, String>> = mutableListOf()

    var sessionId: String? = null
    var userUpdateCount: Int = 0
    var lastUnityCrashId: String? = null
    private var nativeCrashData: NativeCrashData? = null

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

    override val unityCrashId: String?
        get() {
            return lastUnityCrashId
        }

    override fun getLatestNativeCrash(): NativeCrashData? {
        val data = nativeCrashData
        nativeCrashData = null
        return data
    }

    override val symbolsForCurrentArch: Map<String, String>?
        get() {
            TODO("Not yet implemented")
        }

    fun hasNativeCrash(): Boolean = nativeCrashData != null

    fun setNativeCrashData(data: NativeCrashData) {
        nativeCrashData = data
    }
}
