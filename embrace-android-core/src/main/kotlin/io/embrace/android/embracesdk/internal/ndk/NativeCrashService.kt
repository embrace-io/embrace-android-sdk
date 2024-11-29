package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.opentelemetry.api.common.AttributeKey

/**
 * Service to retrieve and delivery native crash data
 */
interface NativeCrashService {

    /**
     * Send and return the most recent native crash. After this method is called, all the cached native crash data
     * will be deleted, including files for crashes that we could not properly load and send.
     */
    fun getAndSendNativeCrash(): NativeCrashData?

    /**
     * Return the data for all native crashes that have been recorded by the SDK
     */
    fun getNativeCrashes(): List<NativeCrashData>

    /**
     * Send the given native crash
     */
    fun sendNativeCrash(
        nativeCrash: NativeCrashData,
        sessionProperties: Map<String, String>,
        metadata: Map<AttributeKey<String>, String> = emptyMap(),
    )

    /**
     * Delete the data files associated with all the native crashes that have been recorded by the SDK
     */
    fun deleteAllNativeCrashes()
}
