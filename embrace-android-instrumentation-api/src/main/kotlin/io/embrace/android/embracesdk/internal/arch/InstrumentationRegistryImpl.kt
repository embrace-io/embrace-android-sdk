package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.worker.Worker
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
class InstrumentationRegistryImpl(
    private val errorHandler: ErrorHandler,
) : InstrumentationRegistry {

    @Volatile
    private var dataSources: Array<DataSource> = emptyArray()
    private val lock = Any()

    @Volatile
    private var closed = false

    override fun onPreSessionEnd() {
        val listeners = dataSources.filterIsInstance<SessionPartEndListener>()
        errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, listeners) {
            it.onPreSessionEnd()
        }
    }

    override fun onPostSessionChange() {
        errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, dataSources.asList()) {
            errorHandler.guardSdkCode(SdkErrorType.SessionPartListenerFail, it::resetDataCaptureLimits)
            if (it is SessionPartChangeListener) {
                it.onPostSessionChange()
            }
        }
    }

    override fun <T : DataSource> add(state: DataSourceFactory<T>): T? {
        val added = synchronized(lock) {
            if (closed) {
                return null
            }
            val dataSource = state() ?: return null
            dataSource.onDataCaptureEnabled()
            dataSources += dataSource // copy-on-write
            dataSource
        }
        return added
    }

    override fun close() {
        val snapshot = synchronized(lock) {
            closed = true
            dataSources.also { dataSources = emptyArray() }
        }
        snapshot.forEach(::disable)
    }

    private fun disable(dataSource: DataSource) {
        errorHandler.guardSdkCode(SdkErrorType.InstrumentationDisableFail, dataSource::onDataCaptureDisabled)
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
        errorHandler.guardSdkCodeEach(SdkErrorType.InstrumentationRegFail, loader) { provider ->
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
        errorHandler.guardSdkCode(SdkErrorType.InstrumentationRegFail) {
            provider.register(args)?.let { add(it) }
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
