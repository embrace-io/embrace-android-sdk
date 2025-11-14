package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector

interface AnrModule {
    val anrService: AnrService?
    val anrOtelMapper: AnrOtelMapper?
    val blockedThreadDetector: BlockedThreadDetector
}
