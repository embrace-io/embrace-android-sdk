package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.internal.utils.Provider

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
    factory: Provider<DataSource<*>>,

    /**
     * Predicate that determines if the service should be enabled or not, via a config value.
     * Defaults to true if not provided.
     */
    private val configGate: Provider<Boolean> = { true },

    /**
     * The type of session that contains the data.
     */
    private var currentSessionType: SessionType? = null,

    /**
     * A session type where data capture should be disabled. For example,
     * background activities capture a subset of sessions.
     */
    private val disabledSessionType: SessionType? = null
) {

    private val enabledDataSource by lazy(factory)
    private var dataSource: DataSource<*>? = null

    init {
        updateDataSource()
    }

    /**
     * Callback that is invoked when the session type changes.
     */
    fun onSessionTypeChange(sessionType: SessionType?) {
        this.currentSessionType = sessionType
        updateDataSource()
        enabledDataSource.resetDataCaptureLimits()
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
                enableDataCapture()
            }
        } else if (!enabled && dataSource != null) {
            dataSource?.disableDataCapture()
            dataSource = null
        }
    }
}
