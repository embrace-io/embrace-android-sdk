package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
class InstrumentationRegistryImpl(
    private val logger: InternalLogger,
) : InstrumentationRegistry {

    private val dataSourceStates = CopyOnWriteArrayList<DataSourceState<*>>()

    override fun onPreSessionEnd() {
        dataSourceStates.toList()
            .filter { it.dataSource is SessionEndListener }
            .map { it.dataSource as SessionEndListener }
            .forEach {
                it.onPreSessionEnd()
            }
    }

    override fun onPostSessionChange() {
        dataSourceStates.toList().forEach {
            it.dataSource?.run {
                resetDataCaptureLimits()
                if (this is SessionChangeListener) {
                    onPostSessionChange()
                }
            }
        }
    }

    override fun add(state: DataSourceState<*>) {
        dataSourceStates.add(state)
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

    override fun getCurrentStates(): Map<EmbraceAttributeKey, Any> {
        val stateAttributes = mutableMapOf<EmbraceAttributeKey, Any>()

        dataSourceStates
            .toList()
            .filter { it.dataSource is StateDataSource<*> }
            .forEach {
                (it.dataSource as StateDataSource<*>).apply {
                    stateAttributes[stateAttributeKey] = getCurrentStateValue()
                }
            }

        return stateAttributes
    }
}
