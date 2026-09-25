package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
class InstrumentationRegistryImpl(
    private val logger: InternalLogger,
) : InstrumentationRegistry {

    private val dataSources = CopyOnWriteArrayList<DataSource>()

    override fun onPreSessionEnd() {
        dataSources
            .filterIsInstance<SessionPartEndListener>()
            .forEach {
                it.onPreSessionEnd()
            }
    }

    override fun onPostSessionChange() {
        dataSources.forEach {
            it.resetDataCaptureLimits()
            if (it is SessionPartChangeListener) {
                it.onPostSessionChange()
            }
        }
    }

    override fun <T : DataSource> add(state: DataSourceState<T>): T? {
        val dataSource = state() ?: return null
        dataSources.add(dataSource)
        // enable data capture once the data source is registered
        dataSource.onDataCaptureEnabled()
        return dataSource
    }

    override fun <T : DataSource> findByType(clazz: KClass<T>): T? {
        return dataSources.firstNotNullOfOrNull { clazz.safeCast(it) }
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
            if (provider.asyncInit) {
                args.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                    registerProvider(provider, args)
                }
            } else {
                registerProvider(provider, args)
            }
        }
    }

    private fun registerProvider(provider: InstrumentationProvider, args: InstrumentationArgs) {
        try {
            provider.register(args)?.let { add(it) }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.InstrumentationRegFail, exc)
        }
    }

    override fun getCurrentStates(): Map<String, Any> {
        val stateAttributes = mutableMapOf<String, Any>()

        dataSources
            .filterIsInstance<StateDataSource<*>>()
            .forEach { stateDataSource ->
                if (stateDataSource.isActive()) {
                    stateAttributes.putAll(stateDataSource.currentStateAttributes())
                }
            }

        return stateAttributes
    }
}
