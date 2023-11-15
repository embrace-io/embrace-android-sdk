package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.NativeCrashData

internal class FakeNdkService : NdkService {
    val propUpdates = mutableListOf<Map<String, String>>()

    override fun updateSessionId(newSessionId: String) {
        TODO("Not yet implemented")
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        propUpdates.add(properties)
    }

    override fun onUserInfoUpdate() {
        TODO("Not yet implemented")
    }

    override fun getUnityCrashId(): String? {
        TODO("Not yet implemented")
    }

    override fun testCrash(isCpp: Boolean) {
        TODO("Not yet implemented")
    }

    override fun checkForNativeCrash(): NativeCrashData? {
        TODO("Not yet implemented")
    }

    override fun getSymbolsForCurrentArch(): Map<String, String>? {
        TODO("Not yet implemented")
    }
}
