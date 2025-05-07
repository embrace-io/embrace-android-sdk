package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.injection.NativeFeatureModule
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService

class FakeNativeFeatureModule(
    override val nativeCrashService: NativeCrashService = FakeNativeCrashService(),
) : NativeFeatureModule
