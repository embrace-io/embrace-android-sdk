package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

interface NdkService {
    fun updateSessionId(newSessionId: String)

    fun onSessionPropertiesUpdate(properties: Map<String, String>)

    fun onUserInfoUpdate()

    val unityCrashId: String?

    /**
     * Get the latest stored [NativeCrashData] instance and purge all existing native crash data.
     */
    fun getLatestNativeCrash(): NativeCrashData?

    /**
     * Retrieves symbol information for the current architecture.
     */
    val symbolsForCurrentArch: Map<String, String>?

    fun initializeService(sessionIdTracker: SessionIdTracker)
}
