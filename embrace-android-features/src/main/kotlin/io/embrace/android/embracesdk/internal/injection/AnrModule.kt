package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource

public interface AnrModule {
    public val anrService: AnrService
    public val anrOtelMapper: AnrOtelMapper
    public val sigquitDataSource: SigquitDataSource
    public val blockedThreadDetector: BlockedThreadDetector
}
