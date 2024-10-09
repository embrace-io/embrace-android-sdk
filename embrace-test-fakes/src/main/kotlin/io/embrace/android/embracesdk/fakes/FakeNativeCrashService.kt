package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import java.util.concurrent.ConcurrentLinkedQueue

class FakeNativeCrashService : NativeCrashService {

    val nativeCrashesSent = ConcurrentLinkedQueue<NativeCrashData>()
    private val nativeCrashDataBlobs = mutableListOf<NativeCrashData>()
    var checkAndSendNativeCrashInvocation: Int = 0

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkAndSendNativeCrashInvocation++
        return nativeCrashDataBlobs.lastOrNull()
    }

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashDataBlobs

    override fun sendNativeCrash(nativeCrash: NativeCrashData) {
        nativeCrashesSent.add(nativeCrash)
    }

    override fun deleteAllNativeCrashes() {
        nativeCrashDataBlobs.clear()
    }

    fun addNativeCrashData(nativeCrashData: NativeCrashData) {
        nativeCrashDataBlobs.add(nativeCrashData)
    }
}
