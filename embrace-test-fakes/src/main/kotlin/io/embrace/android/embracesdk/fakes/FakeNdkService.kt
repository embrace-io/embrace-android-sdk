package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

class FakeNdkService : NdkService {
    val propUpdates: MutableList<Map<String, String>> = mutableListOf()
    var sessionId: String? = null
    var userUpdateCount: Int = 0
    private val nativeCrashDataBlobs = mutableListOf<NativeCrashData>()

    override fun initializeService(sessionIdTracker: SessionIdTracker) {
    }

    override fun deleteAllNativeCrashes() {
        nativeCrashDataBlobs.clear()
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

    override fun getLatestNativeCrash(): NativeCrashData? = nativeCrashDataBlobs.lastOrNull()

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashDataBlobs

    override val symbolsForCurrentArch: Map<String, String>? = null

    fun addNativeCrashData(data: NativeCrashData) {
        nativeCrashDataBlobs.add(data)
    }
}
