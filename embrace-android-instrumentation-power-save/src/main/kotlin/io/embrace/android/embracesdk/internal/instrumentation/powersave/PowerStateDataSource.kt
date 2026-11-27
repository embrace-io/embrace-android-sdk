package io.embrace.android.embracesdk.internal.instrumentation.powersave

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState.PowerMode
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Reports whether power save mode is on as the power state.
 *
 * Where each state has a span of its own, capture is left until power save mode is first on, so that a session part in
 * which nothing odd happens has no span for this state. Where state changes are journaled a state costs no span, so the
 * state is reported from the start: as unknown when capture is enabled, and as normal or low once the mode is read.
 */
class PowerStateDataSource internal constructor(
    private val args: InstrumentationArgs,
    private val powerSaveModeSourceProvider: Provider<PowerSaveModeSource?>,
) : StateDataSource<PowerMode>(
    args = args,
    stateTypeFactory = ::PowerState,
    defaultValue = PowerMode.UNKNOWN,
) {
    constructor(args: InstrumentationArgs) : this(args, { args.powerSaveModeSource() })

    private val receiver =
        PowerSaveModeReceiver(args.errorHandler, powerSaveModeSourceProvider, ::onPowerSaveModeChanged)

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
        // a journaled state says that the mode is normal too. Otherwise nothing is captured until the mode is first low.
        if (journaled || getCurrentStateValue() != PowerMode.UNKNOWN || powerSaveMode) {
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
