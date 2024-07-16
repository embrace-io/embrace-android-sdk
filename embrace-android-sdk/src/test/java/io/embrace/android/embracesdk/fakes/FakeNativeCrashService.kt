package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.payload.NativeCrashData

internal class FakeNativeCrashService : NativeCrashService {

    var data: NativeCrashData? = null
    var checkAndSendNativeCrashInvocation = 0

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkAndSendNativeCrashInvocation++
        return data
    }
}
