package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.capture.cpu.EmbraceCpuInfoDelegate

internal class NativeCoreModuleImpl(initModule: InitModule) : NativeCoreModule {

    override val sharedObjectLoader: SharedObjectLoader by singleton {
        SharedObjectLoader(initModule.logger)
    }

    override val cpuInfoDelegate: CpuInfoDelegate by singleton {
        EmbraceCpuInfoDelegate(sharedObjectLoader, initModule.logger)
    }
}
