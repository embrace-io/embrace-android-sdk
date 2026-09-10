package io.embrace.android.embracesdk.instrumentation.leaks

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class LeakDetectionInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }
        return DataSourceState(
            factory = { LeakDetectionDataSource(args) },
            configGate = { args.configService.autoDataCaptureBehavior.isActivityLeakDetectionEnabled() },
        )
    }
}
