package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService

interface NativeFeatureModule {
    val nativeCrashService: NativeCrashService?
}
