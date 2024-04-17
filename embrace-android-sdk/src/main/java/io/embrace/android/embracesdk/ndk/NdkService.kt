package io.embrace.android.embracesdk.ndk

internal interface NdkService : NativeCrashService {
    fun updateSessionId(newSessionId: String)
    fun onSessionPropertiesUpdate(properties: Map<String, String>)
    fun onUserInfoUpdate()
    fun getUnityCrashId(): String?

    /**
     * Retrieves symbol information for the current architecture.
     */
    fun getSymbolsForCurrentArch(): Map<String, String>?
}
