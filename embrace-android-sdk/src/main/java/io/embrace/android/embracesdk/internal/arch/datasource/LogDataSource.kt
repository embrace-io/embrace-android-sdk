package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter

/**
 * A [DataSource] that adds a new log to the log service.
 */
internal typealias LogDataSource = DataSource<LogWriter>
internal typealias LogDataSourceImpl = DataSourceImpl<LogWriter>
