package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

/**
 * Service to retrieve and delivery native crash data
 */
interface NativeCrashService {

    /**
     * Return the data for all native crashes that have been recorded by the SDK
     */
    fun getNativeCrashes(): List<NativeCrashData>

    /**
     * Send the given native crash
     */
    fun sendNativeCrash(
        nativeCrash: NativeCrashData,
        userSessionProperties: Map<String, String>,
        metadata: Map<String, String>,
    )

    /**
     * Delete the data files associated with all the native crashes that have been recorded by the SDK
     */
    fun deleteAllNativeCrashes()

    /**
     * Delete the data files associated with the given [nativeCrash].
     */
    fun deleteNativeCrash(nativeCrash: NativeCrashData)
}
