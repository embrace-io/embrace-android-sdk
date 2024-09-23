package io.embrace.android.embracesdk.internal.capture.envelope.session

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

/**
 * Handles logic for features that are not fully integrated into the OTel pipeline.
 */
class OtelPayloadMapperImpl(
    private val anrOtelMapper: AnrOtelMapper,
    private val nativeAnrOtelMapper: NativeAnrOtelMapper?,
) : OtelPayloadMapper {

    override fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): List<Span> {
        val cacheAttempt = endType == SessionSnapshotType.PERIODIC_CACHE
        return anrOtelMapper.snapshot(!cacheAttempt)
            .plus(nativeAnrOtelMapper?.snapshot(!cacheAttempt) ?: emptyList())
    }
}
