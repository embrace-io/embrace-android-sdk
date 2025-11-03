package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService

interface NativeFeatureModule {
    val nativeCrashService: NativeCrashService?
}
