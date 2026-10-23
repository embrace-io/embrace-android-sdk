package io.embrace.android.embracesdk.internal.instrumentation.powersave

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
) {
    private val powerSaveModeSourceProvider: Provider<PowerSaveModeSource?> = { args.powerSaveModeSource() }
    private val receiver = PowerSaveModeReceiver(powerSaveModeSourceProvider, ::onPowerSaveModeChanged)

    override val captureStateOnCreation = false

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.backgroundWorker(Worker.Background.NonIoRegWorker).run {
            receiver.register(args.context, this)
            submit {
                powerSaveModeSourceProvider()?.let {
                    onPowerSaveModeChanged(it.isPowerSaveMode())
                }
            }
        }
    }

    override fun onDataCaptureDisabled() {
        receiver.unregister(args.context)
    }

    private fun onPowerSaveModeChanged(powerSaveMode: Boolean) {
        val timestamp = clock.now()
        if (getCurrentStateValue() != PowerMode.UNKNOWN || powerSaveMode) {
            onStateChange(
                newState = if (powerSaveMode) {
                    PowerMode.LOW
                } else {
                    PowerMode.NORMAL
                },
                transitionTimeMs = timestamp,
            )
        }
    }
}
