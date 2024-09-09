package io.embrace.android.embracesdk.internal.anr.ndk

import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

/**
 * Samples the target thread stacktrace when the thread is detected as blocked.
 *
 * The NDK layer must be enabled in order to use this functionality as this class
 * calls native code.
 */
interface NativeThreadSamplerService :
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
