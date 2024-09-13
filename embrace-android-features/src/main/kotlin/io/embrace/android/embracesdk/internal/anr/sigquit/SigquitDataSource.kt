package io.embrace.android.embracesdk.internal.anr.sigquit

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter

interface SigquitDataSource : DataSource<SessionSpanWriter> {
    fun saveSigquit(timestamp: Long)
}
