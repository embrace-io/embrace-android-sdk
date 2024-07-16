package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter

/**
 * A [DataSource] that adds either an event or attribute
 * to the current session span.
 */
internal typealias EventDataSource = DataSource<SessionSpanWriter>
internal typealias EventDataSourceImpl = DataSourceImpl<SessionSpanWriter>
