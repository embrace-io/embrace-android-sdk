package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.ndk.NdkService

internal class FakeNativeModule(
    override val nativeThreadSamplerService: NativeThreadSamplerService? = null,
    override val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller? = null,
    override val ndkService: NdkService = FakeNdkService(),
    override val nativeAnrOtelMapper: NativeAnrOtelMapper = NativeAnrOtelMapper(null, EmbraceSerializer(), FakeClock())
) : NativeModule
