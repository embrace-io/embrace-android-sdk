package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Creates an instance of a data source, or returns null if data capture is disabled (e.g. by config).
 *
 * A service must define an interface that extends [DataSource] for orchestration. This helps enforce
 * testability by making it impossible to register data capture without defining a testable interface.
 */
typealias DataSourceState<T> = () -> T?
