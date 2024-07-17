package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrInterval

internal class FakeNativeThreadSamplerService : NativeThreadSamplerService {

    var symbols: Map<String, String>? = mapOf("armeabi-v7a" to "my-symbols")
    var intervals: List<NativeThreadAnrInterval>? = null

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        TODO("Not yet implemented")
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        TODO("Not yet implemented")
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        TODO("Not yet implemented")
    }

    override fun setupNativeSampler(): Boolean {
        TODO("Not yet implemented")
    }

    override fun monitorCurrentThread(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getNativeSymbols(): Map<String, String>? = symbols

    override fun getCapturedIntervals(receivedTermination: Boolean?): List<NativeThreadAnrInterval>? {
        return intervals
    }

    override fun cleanCollections() {
        TODO("Not yet implemented")
    }
}
