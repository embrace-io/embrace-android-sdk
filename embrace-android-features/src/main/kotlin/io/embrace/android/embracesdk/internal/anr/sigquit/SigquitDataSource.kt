package io.embrace.android.embracesdk.internal.anr.sigquit

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter

public interface SigquitDataSource : DataSource<SessionSpanWriter> {
    public fun saveSigquit(timestamp: Long)
}
