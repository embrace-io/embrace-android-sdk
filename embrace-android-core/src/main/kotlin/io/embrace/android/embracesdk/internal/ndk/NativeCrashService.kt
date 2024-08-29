package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

public interface NativeCrashService {
    public fun getAndSendNativeCrash(): NativeCrashData?
}
