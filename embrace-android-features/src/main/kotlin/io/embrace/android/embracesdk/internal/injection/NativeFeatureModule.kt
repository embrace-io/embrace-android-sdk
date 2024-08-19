package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.ndk.NdkService

public interface NativeFeatureModule {
    public val ndkService: NdkService
    public val nativeThreadSamplerService: NativeThreadSamplerService?
    public val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller?
    public val nativeAnrOtelMapper: NativeAnrOtelMapper
    public val nativeCrashService: NativeCrashService
}
