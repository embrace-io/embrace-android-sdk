package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

class FakeNativeCrashService : NativeCrashService {

    var data: NativeCrashData? = null
    var checkAndSendNativeCrashInvocation: Int = 0

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkAndSendNativeCrashInvocation++
        return data
    }
}
