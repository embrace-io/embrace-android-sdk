package io.embrace.android.embracesdk.internal.capture.envelope.session

import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Handles logic for features that are not fully integrated into the OTel pipeline.
 */
internal class OtelPayloadMapperImpl(
    private val anrOtelMapper: AnrOtelMapper,
    private val nativeAnrOtelMapper: NativeAnrOtelMapper,
    private val sessionPropertiesServiceProvider: Provider<SessionPropertiesService>
) : OtelPayloadMapper {

    override fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): List<Span> {
        val cacheAttempt = endType == SessionSnapshotType.PERIODIC_CACHE
        val appTerminationCause = when {
            crashId != null -> AppTerminationCause.Crash
            else -> null
        }
        if (!cacheAttempt) {
            if (appTerminationCause == null) {
                sessionPropertiesServiceProvider().populateCurrentSession()
            }
        }
        return anrOtelMapper.snapshot(!cacheAttempt)
            .plus(nativeAnrOtelMapper.snapshot(!cacheAttempt))
    }
}
