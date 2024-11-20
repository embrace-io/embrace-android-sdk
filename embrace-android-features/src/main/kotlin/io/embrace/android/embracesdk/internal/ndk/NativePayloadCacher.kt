package io.embrace.android.embracesdk.internal.ndk

/**
 * Caches information that is needed by the native layer to construct a payload post-crash.
 */
interface NativePayloadCacher {

    fun updateSessionId(newSessionId: String)

    fun onSessionPropertiesUpdate(properties: Map<String, String>)

    fun onUserInfoUpdate()
}
