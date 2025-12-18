package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider

/**
 * Base class for [InstrumentationProvider] for a [StateDataSource] of type [T] with a state value of type [S].
 *
 * It ensures the state feature flag is enabled as well as whatever configuration gate is required.
 */
abstract class StateInstrumentationProvider<T : StateDataSource<S>, S : Any>(
    private val configGate: InstrumentationArgs.() -> Boolean = { true },
) : InstrumentationProvider {

    abstract fun factoryProvider(args: InstrumentationArgs): () -> T

    override fun register(args: InstrumentationArgs): DataSourceState<*> {
        return DataSourceState(
            factory = factoryProvider(args),
            configGate = {
                args.configGate() && args.configService.autoDataCaptureBehavior.isStateCaptureEnabled()
            }
        )
    }
}
