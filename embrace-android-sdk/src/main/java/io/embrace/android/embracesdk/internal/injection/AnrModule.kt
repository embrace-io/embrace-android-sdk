package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource

internal interface AnrModule {
    val anrService: AnrService
    val anrOtelMapper: AnrOtelMapper
    val sigquitDataSource: SigquitDataSource
}
