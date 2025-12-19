package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState.PowerMode
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

class PowerStateDataSource(
    private val args: InstrumentationArgs,
) : StateDataSource<PowerMode>(
    args = args,
    stateTypeFactory = ::PowerState,
    defaultValue = PowerMode.UNKNOWN,
    instrumentationName = "power_state_data_source"
) {
    private val powerManagerProvider: Provider<PowerManager?> = { args.systemService(Context.POWER_SERVICE) }
    private val receiver = PowerSaveModeReceiver(powerManagerProvider, ::onPowerSaveModeChanged)

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.backgroundWorker(Worker.Background.NonIoRegWorker).run {
            receiver.register(args.context, this)
            submit {
                powerManagerProvider()?.let {
                    onPowerSaveModeChanged(it.isPowerSaveMode)
                }
            }
        }
    }

    override fun onDataCaptureDisabled(): Unit = receiver.unregister(args.context)

    private fun onPowerSaveModeChanged(powerSaveMode: Boolean) {
        val timestamp = clock.now()
        val newState = if (powerSaveMode) {
            PowerMode.LOW
        } else {
            PowerMode.NORMAL
        }
        onStateChange(timestamp, newState)
    }
}
