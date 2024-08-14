package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.fakes.FakeNdkService
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.injection.NativeModule
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer

internal class FakeNativeModule(
    override val nativeThreadSamplerService: NativeThreadSamplerService? = null,
    override val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller? = null,
    override val ndkService: NdkService = FakeNdkService(),
    override val nativeAnrOtelMapper: NativeAnrOtelMapper = NativeAnrOtelMapper(null, EmbraceSerializer(), FakeClock()),
    override val nativeCrashService: NativeCrashService = FakeNativeCrashService(),
    override val sharedObjectLoader: SharedObjectLoader = SharedObjectLoader(EmbLoggerImpl()),
    override val cpuInfoDelegate: CpuInfoDelegate = FakeCpuInfoDelegate(),
) : NativeModule
