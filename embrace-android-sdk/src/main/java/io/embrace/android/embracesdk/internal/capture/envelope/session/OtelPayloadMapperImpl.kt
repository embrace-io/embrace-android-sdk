package io.embrace.android.embracesdk.internal.capture.envelope.session

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Handles logic for features that are not fully integrated into the OTel pipeline.
 */
internal class OtelPayloadMapperImpl(
    private val anrOtelMapper: AnrOtelMapper,
    private val nativeAnrOtelMapperProvider: Provider<NativeAnrOtelMapper?>,
) : OtelPayloadMapper {

    override fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): List<Span> {
        val cacheAttempt = endType == SessionSnapshotType.PERIODIC_CACHE
        return anrOtelMapper.snapshot(!cacheAttempt)
            .plus(nativeAnrOtelMapperProvider()?.snapshot(!cacheAttempt) ?: emptyList())
    }
}
