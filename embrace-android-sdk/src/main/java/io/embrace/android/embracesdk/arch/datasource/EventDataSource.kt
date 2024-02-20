package io.embrace.android.embracesdk.arch.datasource

import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter

/**
 * A [DataSource] that adds either a [EmbraceSpanEvent] or [EmbraceSpanAttribute]
 * to the current session span.
 */
internal typealias EventDataSource = DataSource<SessionSpanWriter>
internal typealias EventDataSourceImpl = DataSourceImpl<SessionSpanWriter>
