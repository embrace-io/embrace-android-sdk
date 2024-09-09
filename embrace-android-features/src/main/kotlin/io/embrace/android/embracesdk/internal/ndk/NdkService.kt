package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

interface NdkService {
    fun updateSessionId(newSessionId: String)

    fun onSessionPropertiesUpdate(properties: Map<String, String>)

    fun onUserInfoUpdate()

    val unityCrashId: String?

    /**
     * Get and delete the stored [NativeCrashData] from a previous instance of the app that ended in a native crash if it exists
     */
    fun getNativeCrash(): NativeCrashData?

    /**
     * Retrieves symbol information for the current architecture.
     */
    val symbolsForCurrentArch: Map<String, String>?

    fun initializeService()
}
