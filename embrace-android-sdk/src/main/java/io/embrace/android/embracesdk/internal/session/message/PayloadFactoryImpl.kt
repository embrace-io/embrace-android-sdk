package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.LifeEventType
import io.embrace.android.embracesdk.payload.SessionZygote

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
            ProcessState.FOREGROUND -> endSessionWithState(initial)
            ProcessState.BACKGROUND -> endBackgroundActivityWithState(initial)
        }

    override fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
        crashId: String
    ) = when (state) {
        ProcessState.FOREGROUND -> endSessionWithCrash(initial, crashId)
        ProcessState.BACKGROUND -> endBackgroundActivityWithCrash(initial, crashId)
    }

    override fun snapshotPayload(state: ProcessState, timestamp: Long, initial: SessionZygote) =
        when (state) {
            ProcessState.FOREGROUND -> snapshotSession(initial)
            ProcessState.BACKGROUND -> snapshotBackgroundActivity(initial)
        }

    override fun startSessionWithManual(timestamp: Long): SessionZygote {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.MANUAL,
                timestamp,
                ApplicationState.FOREGROUND
            )
        )
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionZygote): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                backgroundActivityEnabled = isBackgroundActivityEnabled(),
            )
        )
    }

    private fun startSessionWithState(timestamp: Long, coldStart: Boolean): SessionZygote {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams(
                coldStart,
                LifeEventType.STATE,
                timestamp,
                ApplicationState.FOREGROUND
            )
        )
    }

    private fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): SessionZygote? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }

        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val time = when {
            coldStart -> timestamp
            else -> timestamp + 1
        }
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams(
                coldStart = coldStart,
                startType = LifeEventType.BKGND_STATE,
                startTime = time,
                ApplicationState.BACKGROUND
            )
        )
    }

    private fun endSessionWithState(initial: SessionZygote): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                backgroundActivityEnabled = isBackgroundActivityEnabled(),
            )
        )
    }

    private fun endBackgroundActivityWithState(initial: SessionZygote): Envelope<SessionPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }

        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                backgroundActivityEnabled = true,
            )
        )
    }

    private fun endSessionWithCrash(
        initial: SessionZygote,
        crashId: String
    ): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger,
                backgroundActivityEnabled = isBackgroundActivityEnabled(),
                crashId = crashId
            )
        )
    }

    private fun endBackgroundActivityWithCrash(
        initial: SessionZygote,
        crashId: String
    ): Envelope<SessionPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger,
                backgroundActivityEnabled = true,
                crashId = crashId
            )
        )
    }

    /**
     * Called when the session is persisted every 2s to cache its state.
     */
    private fun snapshotSession(initial: SessionZygote): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.PERIODIC_CACHE,
                logger = logger,
                backgroundActivityEnabled = isBackgroundActivityEnabled()
            )
        )
    }

    private fun snapshotBackgroundActivity(initial: SessionZygote): Envelope<SessionPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.PERIODIC_CACHE,
                logger = logger,
                backgroundActivityEnabled = true
            )
        )
    }

    private fun isBackgroundActivityEnabled(): Boolean = configService.isBackgroundActivityCaptureEnabled()
}
