package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

public class FakeNdkService : NdkService {
    public var checkForNativeCrashCount: Int = 0
    public val propUpdates: MutableList<Map<String, String>> = mutableListOf()

    public var sessionId: String? = null
    public var userUpdateCount: Int = 0
    public var lastUnityCrashId: String? = null
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

    public fun hasNativeCrash(): Boolean = nativeCrashData != null

    public fun setNativeCrashData(data: NativeCrashData) {
        nativeCrashData = data
    }
}
