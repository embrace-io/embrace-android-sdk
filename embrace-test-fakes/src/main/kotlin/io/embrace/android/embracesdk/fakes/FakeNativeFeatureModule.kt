package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService

class FakeNativeFeatureModule(
    override val nativeCrashService: NativeCrashService = FakeNativeCrashService(),
) : NativeFeatureModule
