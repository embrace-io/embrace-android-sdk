package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

public interface NdkService : NativeCrashService {
    public fun updateSessionId(newSessionId: String)

    public fun onSessionPropertiesUpdate(properties: Map<String, String>)

    public fun onUserInfoUpdate()

    public fun getUnityCrashId(): String?

    /**
     * Get and delete the stored [NativeCrashData] from a previous instance of the app that ended in a native crash if it exists
     */
    public fun getNativeCrash(): NativeCrashData?

    /**
     * Retrieves symbol information for the current architecture.
     */
    public fun getSymbolsForCurrentArch(): Map<String, String>?
}
