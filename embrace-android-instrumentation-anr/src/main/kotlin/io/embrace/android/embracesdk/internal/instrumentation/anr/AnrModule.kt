package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector

interface AnrModule {
    val anrService: AnrService?
    val anrOtelMapper: OtelPayloadMapper?
    val blockedThreadDetector: BlockedThreadDetector
}
