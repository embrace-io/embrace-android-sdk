package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.NativeCrashData

internal class FakeNdkService : NdkService {
    var checkForNativeCrashCount: Int = 0
    val propUpdates = mutableListOf<Map<String, String>>()

    var sessionId: String? = null
    var userUpdateCount: Int = 0
    var lastUnityCrashId: String? = null

    override fun updateSessionId(newSessionId: String) {
        sessionId = newSessionId
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        propUpdates.add(properties)
    }

    override fun onUserInfoUpdate() {
        userUpdateCount++
    }

    override fun getUnityCrashId(): String? {
        return lastUnityCrashId
    }

    override fun checkForNativeCrash(): NativeCrashData? {
        checkForNativeCrashCount++
        return null
    }

    override fun getSymbolsForCurrentArch(): Map<String, String>? {
        TODO("Not yet implemented")
    }
}
