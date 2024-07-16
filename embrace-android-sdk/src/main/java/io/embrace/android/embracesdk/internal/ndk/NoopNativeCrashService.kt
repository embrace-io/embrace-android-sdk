package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.payload.NativeCrashData

/**
 * [NativeCrashService] used when the native features are not enabled
 */
internal class NoopNativeCrashService : NativeCrashService {
    override fun getAndSendNativeCrash(): NativeCrashData? = null
}
