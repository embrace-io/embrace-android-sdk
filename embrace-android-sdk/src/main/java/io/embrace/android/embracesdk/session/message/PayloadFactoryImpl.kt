package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class PayloadFactoryImpl(
    private val payloadMessageCollator: PayloadMessageCollator,
    private val configService: ConfigService,
    private val logger: EmbLogger
) : PayloadFactory {

    override fun startPayloadWithState(state: ProcessState, timestamp: Long, coldStart: Boolean) =
        when (state) {
            ProcessState.FOREGROUND -> startSessionWithState(timestamp, coldStart)
            ProcessState.BACKGROUND -> startBackgroundActivityWithState(timestamp, coldStart)
        }

    override fun endPayloadWithState(state: ProcessState, timestamp: Long, initial: SessionZygote) =
        when (state) {
            ProcessState.FOREGROUND -> endSessionWithState(initial, timestamp)
            ProcessState.BACKGROUND -> endBackgroundActivityWithState(initial, timestamp)
        }

    override fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
        crashId: String
    ) = when (state) {
        ProcessState.FOREGROUND -> endSessionWithCrash(initial, timestamp, crashId)
        ProcessState.BACKGROUND -> endBackgroundActivityWithCrash(initial, timestamp, crashId)
    }

    override fun snapshotPayload(state: ProcessState, timestamp: Long, initial: SessionZygote) =
        when (state) {
            ProcessState.FOREGROUND -> snapshotSession(initial, timestamp)
            ProcessState.BACKGROUND -> snapshotBackgroundActivity(initial, timestamp)
        }

    override fun startSessionWithManual(timestamp: Long): SessionZygote {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.MANUAL,
                timestamp
            )
        )
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionZygote): SessionMessage {
        return payloadMessageCollator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.MANUAL,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger
            )
        )
    }

    private fun startSessionWithState(timestamp: Long, coldStart: Boolean): SessionZygote {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                coldStart,
                LifeEventType.STATE,
                timestamp
            )
        )
    }

    private fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): SessionZygote? {
        if (!configService.isBackgroundActivityCaptureEnabled()) {
            return null
        }

        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val time = when {
            coldStart -> timestamp
            else -> timestamp + 1
        }
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                coldStart = coldStart,
                startType = LifeEventType.BKGND_STATE,
                startTime = time
            )
        )
    }

    private fun endSessionWithState(initial: SessionZygote, timestamp: Long): SessionMessage {
        return payloadMessageCollator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger
            )
        )
    }

    private fun endBackgroundActivityWithState(initial: SessionZygote, timestamp: Long): SessionMessage? {
        if (!configService.isBackgroundActivityCaptureEnabled()) {
            return null
        }

        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        return payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp - 1,
                lifeEventType = LifeEventType.BKGND_STATE,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger
            )
        )
    }

    private fun endSessionWithCrash(
        initial: SessionZygote,
        timestamp: Long,
        crashId: String
    ): SessionMessage {
        return payloadMessageCollator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                crashId = crashId,
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger
            )
        )
    }

    private fun endBackgroundActivityWithCrash(
        initial: SessionZygote,
        timestamp: Long,
        crashId: String
    ): SessionMessage? {
        if (!configService.isBackgroundActivityCaptureEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.BKGND_STATE,
                crashId = crashId,
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger
            )
        )
    }

    /**
     * Called when the session is persisted every 2s to cache its state.
     */
    private fun snapshotSession(initial: SessionZygote, timestamp: Long): SessionMessage {
        return payloadMessageCollator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                endType = SessionSnapshotType.PERIODIC_CACHE,
                logger = logger
            )
        )
    }

    private fun snapshotBackgroundActivity(initial: SessionZygote, timestamp: Long): SessionMessage? {
        if (!configService.isBackgroundActivityCaptureEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = null,
                endType = SessionSnapshotType.PERIODIC_CACHE,
                logger = logger
            )
        )
    }
}
