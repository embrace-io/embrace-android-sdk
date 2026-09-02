package io.embrace.android.embracesdk.instrumentation.leaks

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

/**
 * SPI entry point for Activity leak detection. Registers nothing below API 23, since GC cycle tracking relies on the
 * runtime stat `Debug.getRuntimeStat` only reports from that version onward.
 */
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
