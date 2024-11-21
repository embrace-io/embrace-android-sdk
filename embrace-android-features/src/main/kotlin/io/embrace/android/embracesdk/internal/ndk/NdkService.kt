package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

interface NdkService : NativeCrashProcessor, SymbolService {
    fun updateSessionId(newSessionId: String)

    fun onSessionPropertiesUpdate(properties: Map<String, String>)

    fun onUserInfoUpdate()

    fun initializeService(sessionIdTracker: SessionIdTracker)
}
