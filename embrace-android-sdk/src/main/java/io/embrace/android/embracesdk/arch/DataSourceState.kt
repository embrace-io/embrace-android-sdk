package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Holds the current state of the service. This class automatically handles changes in config
 * that enable/disable the service, and creates new instances of the service as required.
 * It also is capable of disabling the service if the [SessionType] is not supported.
 */
internal class DataSourceState(

    /**
     * Provides instances of services. A service must define an interface
     * that extends [DataSource] for orchestration. This helps enforce testability
     * by making it impossible to register data capture without defining a testable interface.
     */
    factory: () -> DataSource,

    /**
     * Predicate that determines if the service should be enabled or not, via a config value.
     * Defaults to true if not provided.
     */
    private val configGate: () -> Boolean = { true },

    /**
     * The type of session that contains the data.
     */
    private var currentSessionType: SessionType? = null,

    /**
     * A session type where data capture should be disabled. For example,
     * background activities capture a subset of sessions.
     */
    private val disabledSessionType: SessionType? = null,

    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) {

    private val enabledDataSource by lazy(factory)
    private var dataSource: DataSource? = null

    init {
        updateDataSource()
    }

    /**
     * Callback that is invoked when the session type changes.
     */
    fun onSessionTypeChange(sessionType: SessionType?) {
        this.currentSessionType = sessionType
        updateDataSource()
    }

    /**
     * Callback that is invoked when the config layer experiences a change.
     */
    fun onConfigChange() {
        updateDataSource()
    }

    private fun updateDataSource() {
        val enabled =
            currentSessionType != null && currentSessionType != disabledSessionType && configGate()

        if (enabled && dataSource == null) {
            dataSource = enabledDataSource.apply {
                try {
                    registerListeners()
                } catch (exc: Throwable) {
                    logger.logError("Failed to register listener", exc)
                }
            }
        } else if (!enabled && dataSource != null) {
            try {
                dataSource?.unregisterListeners()
            } catch (exc: Throwable) {
                logger.logError("Failed to unregister listener", exc)
            }
            dataSource = null
        }
    }
}
