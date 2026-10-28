package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Creates an instance of a data source, or returns null if data capture is disabled (e.g. by config).
 *
 * A service must define an interface that extends [DataSource] for orchestration. This helps enforce
 * testability by making it impossible to register data capture without defining a testable interface.
 *
 * The instrumentation registry runs it outside its lock, so it can run while the SDK is being disabled.
 * The cleanup and freeing of resources related to the construction of the data source should currently
 * be handled in [DataSource.onDataCaptureDisabled], so even if a data source was never enabled, it has
 * to be disabled when the registry closes.
 */
typealias DataSourceFactory<T> = () -> T?
