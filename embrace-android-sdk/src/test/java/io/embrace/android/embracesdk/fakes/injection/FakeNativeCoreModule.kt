package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.injection.NativeCoreModule
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl

public class FakeNativeCoreModule(
    override val sharedObjectLoader: SharedObjectLoader = SharedObjectLoader(EmbLoggerImpl()),
    override val cpuInfoDelegate: CpuInfoDelegate = FakeCpuInfoDelegate(),
) : NativeCoreModule
