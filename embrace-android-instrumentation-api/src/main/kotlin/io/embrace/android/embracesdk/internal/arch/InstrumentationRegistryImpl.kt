package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
class InstrumentationRegistryImpl(
    private val worker: BackgroundWorker,
    private val logger: EmbLogger,
) : InstrumentationRegistry {

    private val dataSourceStates = CopyOnWriteArrayList<DataSourceState<*>>()

    override var currentSessionType: SessionType? = null
        set(value) {
            field = value
            onSessionTypeChange()
        }

    override fun add(state: DataSourceState<*>) {
        dataSourceStates.add(state)
        state.dispatchStateChange {
            state.currentSessionType = currentSessionType
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : DataSource> findByType(clazz: KClass<T>): T? {
        val element = dataSourceStates.firstOrNull {
            val obj = it.dataSource
            obj != null && clazz.isInstance(obj)
        }
        val state = element as? DataSourceState<T>
        return state?.dataSource
    }

    /**
     * Loads instrumentation via SPI and registers it with the SDK.
     */
    override fun loadInstrumentations(
        instrumentationProviders: Iterable<InstrumentationProvider>,
        args: InstrumentationArgs,
    ) {
        val loader = instrumentationProviders.sortedBy { it.priority }
        loader.forEach { provider ->
            try {
                provider.register(args)?.let { dataSourceState ->
                    add(dataSourceState)
                }
            } catch (exc: Throwable) {
                logger.trackInternalError(InternalErrorType.INSTRUMENTATION_REG_FAIL, exc)
            }
        }
    }

    /**
     * Callback that is invoked when the session type changes.
     */
    private fun onSessionTypeChange() {
        dataSourceStates.forEach { state ->
            try {
                // alter the session type - some data sources don't capture for background activities.
                state.dispatchStateChange {
                    state.currentSessionType = currentSessionType
                }
            } catch (exc: Throwable) {
                logger.trackInternalError(InternalErrorType.SESSION_CHANGE_DATA_CAPTURE_FAIL, exc)
            }
        }
    }

    private fun DataSourceState<*>.dispatchStateChange(action: () -> Unit) {
        if (asyncInit) {
            worker.submit(action)
        } else {
            action()
        }
    }
}
