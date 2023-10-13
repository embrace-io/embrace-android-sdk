package io.embrace.android.embracesdk.ndk

import io.embrace.android.embracesdk.payload.NativeCrashData

internal interface NdkService {
    fun updateSessionId(newSessionId: String)
    fun onSessionPropertiesUpdate(properties: Map<String, String>)
    fun onUserInfoUpdate()
    fun getUnityCrashId(): String?

    // TODO: remove this. Only for testing purposes.
    fun testCrash(isCpp: Boolean)
    fun checkForNativeCrash(): NativeCrashData?

    /**
     * Retrieves symbol information for the current architecture.
     */
    fun getSymbolsForCurrentArch(): Map<String, String>?
}
