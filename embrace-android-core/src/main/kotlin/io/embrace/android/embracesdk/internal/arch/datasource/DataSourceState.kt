package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.SessionType
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Holds the current state of the service. This class automatically handles changes in config
 * that enable/disable the service, and creates new instances of the service as required.
 * It also is capable of disabling the service if the [SessionType] is not supported.
 */
public class DataSourceState<T : DataSource<*>>(

    /**
     * Provides instances of services. A service must define an interface
     * that extends [DataSource] for orchestration. This helps enforce testability
     * by making it impossible to register data capture without defining a testable interface.
     */
    factory: Provider<T?>,

    /**
     * Predicate that determines if the service should be enabled or not, via a config value.
     * Defaults to true if not provided.
     */
    private val configGate: Provider<Boolean> = { true },

    /**
     * A session type where data capture should be disabled. For example,
     * background activities capture a subset of sessions.
     */
    private val disabledSessionType: SessionType? = null,

    /**
     * Whether this feature supports being initialized asynchronously. Defaults to false. If
     * the feature is set to true the feature will be initialized on a background thread.
     *
     * If you enable this behavior please ensure your implementation is thread safe (e.g.
     * it can handle unbalanced calls to [enableDataCapture] and others).
     */
    public val asyncInit: Boolean = false
) {

    /**
     * The type of session that contains the data.
     */
    public var currentSessionType: SessionType? = null
        set(value) {
            field = value
            onSessionTypeChange()
        }

    private val factoryRef = lazy(factory)

    public var dataSource: T? = null
        private set

    init {
        updateDataSource()
    }

    /**
     * Callback that is invoked when the session type changes.
     */
    private fun onSessionTypeChange() {
        updateDataSource()
        if (factoryRef.isInitialized()) {
            factoryRef.value?.resetDataCaptureLimits()
        }
    }

    /**
     * Callback that is invoked when the config layer experiences a change.
     */
    public fun onConfigChange() {
        updateDataSource()
    }

    private fun updateDataSource() {
        val enabled =
            currentSessionType != null && currentSessionType != disabledSessionType && configGate()

        if (enabled && dataSource == null) {
            dataSource = factoryRef.value?.apply {
                enableDataCapture()
            }
        } else if (!enabled && dataSource != null) {
            dataSource?.disableDataCapture()
            dataSource = null
        }
    }
}
