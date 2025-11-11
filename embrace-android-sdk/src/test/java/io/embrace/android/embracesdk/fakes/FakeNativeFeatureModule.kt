package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeFeatureModule

class FakeNativeFeatureModule(
    override val nativeCrashService: NativeCrashService = FakeNativeCrashService(),
) : NativeFeatureModule
