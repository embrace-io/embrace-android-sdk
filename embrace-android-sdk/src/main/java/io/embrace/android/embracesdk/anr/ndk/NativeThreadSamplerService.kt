package io.embrace.android.embracesdk.anr.ndk

import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.session.MemoryCleanerListener

/**
 * Samples the target thread stacktrace when the thread is detected as blocked.
 *
 * The NDK layer must be enabled in order to use this functionality as this class
 * calls native code.
 */
internal interface NativeThreadSamplerService :
    BlockedThreadListener,
    MemoryCleanerListener {

    /**
     * Performs one-time setup of the native stacktrace sampler (but doesn't start any monitoring).
     */
    fun setupNativeSampler(): Boolean

    /**
     * Monitors the current thread.
     */
    fun monitorCurrentThread(): Boolean

    fun getNativeSymbols(): Map<String, String>?

    fun getCapturedIntervals(receivedTermination: Boolean?): List<NativeThreadAnrInterval>?
}
