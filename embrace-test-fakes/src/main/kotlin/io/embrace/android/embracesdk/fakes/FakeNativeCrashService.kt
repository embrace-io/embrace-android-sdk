package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

public class FakeNativeCrashService : NativeCrashService {

    public var data: NativeCrashData? = null
    public var checkAndSendNativeCrashInvocation: Int = 0

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkAndSendNativeCrashInvocation++
        return data
    }
}
