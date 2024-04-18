package io.embrace.android.embracesdk.ndk

import io.embrace.android.embracesdk.payload.NativeCrashData

internal interface NativeCrashService {
    fun getAndSendNativeCrash(): NativeCrashData?
}
