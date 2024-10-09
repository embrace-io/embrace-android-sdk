package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

interface NativeCrashService {
    fun getAndSendNativeCrash(): NativeCrashData?

    fun getNativeCrashes(): List<NativeCrashData>

    fun sendNativeCrash(nativeCrash: NativeCrashData)

    fun deleteAllNativeCrashes()
}
