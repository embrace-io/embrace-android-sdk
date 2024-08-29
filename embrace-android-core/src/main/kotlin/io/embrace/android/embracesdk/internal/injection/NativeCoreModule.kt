package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate

public interface NativeCoreModule {
    public val sharedObjectLoader: SharedObjectLoader
    public val cpuInfoDelegate: CpuInfoDelegate
}
