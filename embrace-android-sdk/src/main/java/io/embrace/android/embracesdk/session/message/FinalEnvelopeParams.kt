package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.internal.StartupEventInfo
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.LifeEventType
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
internal sealed class FinalEnvelopeParams(
    val initial: SessionZygote,
    val endTime: Long,
    val lifeEventType: LifeEventType?,
    crashId: String?,
    val endType: SessionSnapshotType,
    val isCacheAttempt: Boolean,
    val captureSpans: Boolean,
    val logger: EmbLogger
) {

    val crashId: String? = when {
        crashId.isNullOrEmpty() -> null
        else -> crashId
    }

    abstract val terminationTime: Long?
    abstract val receivedTermination: Boolean?
    abstract val endTimeVal: Long?
    abstract fun getStartupEventInfo(eventService: EventService): StartupEventInfo?

    /**
     * Initial parameters required to create a background activity object.
     */
    internal class BackgroundActivityParams(
        initial: SessionZygote,
        endTime: Long,
        lifeEventType: LifeEventType?,
        endType: SessionSnapshotType,
        logger: EmbLogger,
        captureSpans: Boolean = true,
        crashId: String? = null,
    ) : FinalEnvelopeParams(
        initial,
        endTime,
        lifeEventType,
        crashId,
        endType,
        lifeEventType == null,
        captureSpans,
        logger
    ) {
        override val terminationTime: Long? = null
        override val receivedTermination: Boolean? = null
        override val endTimeVal: Long? = null
        override fun getStartupEventInfo(eventService: EventService): StartupEventInfo? = null
    }

    /**
     * Initial parameters required to create a session object.
     */
    internal class SessionParams(
        initial: SessionZygote,
        endTime: Long,
        lifeEventType: LifeEventType?,
        endType: SessionSnapshotType,
        logger: EmbLogger,
        captureSpans: Boolean = true,
        crashId: String? = null,
    ) : FinalEnvelopeParams(
        initial,
        endTime,
        lifeEventType,
        crashId,
        endType,
        endType == SessionSnapshotType.PERIODIC_CACHE,
        captureSpans,
        logger
    ) {

        override val terminationTime: Long? = when {
            endType.forceQuit -> endTime
            else -> null
        }
        override val receivedTermination: Boolean? = when {
            endType.forceQuit -> true
            else -> null
        }

        // We don't set end time for force-quit, as the API interprets this to be a clean
        // termination
        override val endTimeVal: Long? = when {
            endType.forceQuit -> null
            else -> endTime
        }

        override fun getStartupEventInfo(eventService: EventService) = when {
            initial.isColdStart -> captureDataSafely(logger, eventService::getStartupMomentInfo)
            else -> null
        }
    }
}
