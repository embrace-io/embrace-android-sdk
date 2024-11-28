package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.injection.NativeFeatureModule
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer

class FakeNativeFeatureModule(
    override val nativeThreadSamplerService: NativeThreadSamplerService? = null,
    override val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller? = null,
    override val nativeAnrOtelMapper: NativeAnrOtelMapper = NativeAnrOtelMapper(null, EmbraceSerializer(), FakeClock()),
    override val nativeCrashService: NativeCrashService = FakeNativeCrashService(),
) : NativeFeatureModule
