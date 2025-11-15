package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

interface NativeFeatureModule {
    val nativeCrashService: NativeCrashService?
}
