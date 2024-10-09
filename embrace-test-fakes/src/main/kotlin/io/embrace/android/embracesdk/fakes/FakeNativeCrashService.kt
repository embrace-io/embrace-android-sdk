package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

class FakeNativeCrashService : NativeCrashService {

    private val nativeCrashDataBlobs = mutableListOf<NativeCrashData>()
    var checkAndSendNativeCrashInvocation: Int = 0

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkAndSendNativeCrashInvocation++
        return nativeCrashDataBlobs.lastOrNull()
    }

    fun addNativeCrashData(nativeCrashData: NativeCrashData) {
        nativeCrashDataBlobs.add(nativeCrashData)
    }
}
