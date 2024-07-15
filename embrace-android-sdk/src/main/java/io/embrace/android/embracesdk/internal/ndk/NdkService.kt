package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.payload.NativeCrashData

internal interface NdkService : NativeCrashService {
    fun updateSessionId(newSessionId: String)

    fun onSessionPropertiesUpdate(properties: Map<String, String>)

    fun onUserInfoUpdate()

    fun getUnityCrashId(): String?

    /**
     * Get and delete the stored [NativeCrashData] from a previous instance of the app that ended in a native crash if it exists
     */
    fun getNativeCrash(): NativeCrashData?

    /**
     * Retrieves symbol information for the current architecture.
     */
    fun getSymbolsForCurrentArch(): Map<String, String>?
}
