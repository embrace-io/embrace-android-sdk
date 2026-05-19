package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Holds the current state of the service. This class automatically handles changes in config
 * that enable/disable the service, and creates new instances of the service as required.
 * It also is capable of disabling the service if the SessionType is not supported.
 */
class DataSourceState<T : DataSource>(

    /**
     * Provides instances of services. A service must define an interface
     * that extends [DataSource] for orchestration. This helps enforce testability
     * by making it impossible to register data capture without defining a testable interface.
     */
    factory: () -> T?,

    /**
     * Predicate that determines if the service should be enabled or not, via a config value.
     * Defaults to true if not provided.
     */
    configGate: () -> Boolean = { true },
) {

    private val factoryRef = lazy(factory)

    var dataSource: T? = when {
        configGate() -> factoryRef.value?.apply {
            onDataCaptureEnabled()
        }
        else -> null
    }
}
