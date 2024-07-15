package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

internal class FakeNdkService : NdkService {
    var checkForNativeCrashCount: Int = 0
    val propUpdates = mutableListOf<Map<String, String>>()

    var sessionId: String? = null
    var userUpdateCount: Int = 0
    var lastUnityCrashId: String? = null
    private var nativeCrashData: NativeCrashData? = null

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

    override fun getNativeCrash(): NativeCrashData? {
        val data = nativeCrashData
        nativeCrashData = null
        return data
    }

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkForNativeCrashCount++
        return getNativeCrash()
    }

    override fun getSymbolsForCurrentArch(): Map<String, String>? {
        TODO("Not yet implemented")
    }

    fun hasNativeCrash(): Boolean = nativeCrashData != null

    fun setNativeCrashData(data: NativeCrashData) {
        nativeCrashData = data
    }
}
