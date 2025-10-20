package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter

/**
 * A [DataSource] that adds either an event or attribute
 * to the current session span.
 */
typealias EventDataSource = DataSource<SessionSpanWriter>
typealias EventDataSourceImpl = DataSourceImpl<SessionSpanWriter>
