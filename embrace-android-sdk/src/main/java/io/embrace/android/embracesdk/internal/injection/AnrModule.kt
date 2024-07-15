package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.sigquit.SigquitDataSource

internal interface AnrModule {
    val anrService: AnrService
    val anrOtelMapper: AnrOtelMapper
    val sigquitDataSource: SigquitDataSource
}
