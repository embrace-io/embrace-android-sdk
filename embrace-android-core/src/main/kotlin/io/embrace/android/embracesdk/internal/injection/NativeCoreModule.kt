package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate

interface NativeCoreModule {
    val sharedObjectLoader: SharedObjectLoader
    val cpuInfoDelegate: CpuInfoDelegate
}
