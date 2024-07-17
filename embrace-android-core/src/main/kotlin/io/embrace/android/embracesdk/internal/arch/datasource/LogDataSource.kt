package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter

/**
 * A [DataSource] that adds a new log to the log service.
 */
public typealias LogDataSource = DataSource<LogWriter>
public typealias LogDataSourceImpl = DataSourceImpl<LogWriter>
