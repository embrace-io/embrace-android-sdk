package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData

/**
 * [NativeCrashService] used when the native features are not enabled
 */
public class NoopNativeCrashService : NativeCrashService {
    override fun getAndSendNativeCrash(): NativeCrashData? = null
}
