package io.embrace.android.embracesdk.internal.instrumentation.powersave

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class PowerStateInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*> {
        return DataSourceState(
            factory = { PowerStateDataSource(args) },
            configGate = {
                args.configService.autoDataCaptureBehavior.isStateCaptureEnabled() &&
                    args.configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled()
            }
        )
    }
}
