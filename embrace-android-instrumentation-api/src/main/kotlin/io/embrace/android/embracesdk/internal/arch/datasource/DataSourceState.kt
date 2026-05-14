package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Holds the current state of the service. This class automatically handles changes in config
 * that enable/disable the service, and creates new instances of the service as required.
 * It also is capable of disabling the service if the SessionType is not supported.
 *
 * This is tightly bound to [DataSourceImpl] rather than [DataSource] so that it can consistently enforce the creation/enablement
 * lifecycle [DataSourceImpl] defines. Pulling that up to [DataSource] requires implementations to adhere to a non-trivial contract
 * that are too burdensome to put on them.
 */
class DataSourceState<T : DataSourceImpl>(

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
            if (enableOnCreate) {
                enable()
            }
        }
        else -> null
    }
}
