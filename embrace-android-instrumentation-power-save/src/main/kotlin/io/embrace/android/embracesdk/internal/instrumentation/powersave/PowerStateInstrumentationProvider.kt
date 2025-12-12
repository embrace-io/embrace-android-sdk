package io.embrace.android.embracesdk.internal.instrumentation.powersave

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class PowerStateInstrumentationProvider :
    StateInstrumentationProvider<PowerStateDataSource, SchemaType.PowerState.PowerMode>(
        configGate = {
            configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled()
        }
    ) {
    override fun factoryProvider(args: InstrumentationArgs): () -> PowerStateDataSource {
        return { PowerStateDataSource(args) }
    }
}
