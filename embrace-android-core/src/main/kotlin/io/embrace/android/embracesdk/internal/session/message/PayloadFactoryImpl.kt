package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

internal class PayloadFactoryImpl(
    private val payloadMessageCollator: PayloadMessageCollator,
    private val logEnvelopeSource: LogEnvelopeSource,
    private val configService: ConfigService,
    private val logger: EmbLogger,
) : PayloadFactory {

    override fun startPayloadWithState(state: AppState, timestamp: Long, coldStart: Boolean): SessionZygote? =
        when (state) {
            AppState.FOREGROUND -> startSessionWithState(timestamp, coldStart)
            AppState.BACKGROUND -> startBackgroundActivityWithState(timestamp, coldStart)
        }

    override fun endPayloadWithState(
        state: AppState,
        timestamp: Long,
        initial: SessionZygote,
    ): Envelope<SessionPayload>? =
        when (state) {
            AppState.FOREGROUND -> endSessionWithState(initial)
            AppState.BACKGROUND -> endBackgroundActivityWithState(initial)
        }

    override fun endPayloadWithCrash(
        state: AppState,
        timestamp: Long,
        initial: SessionZygote,
        crashId: String,
    ): Envelope<SessionPayload>? = when (state) {
        AppState.FOREGROUND -> endSessionWithCrash(initial, crashId)
        AppState.BACKGROUND -> endBackgroundActivityWithCrash(initial, crashId)
    }

    override fun snapshotPayload(
        state: AppState,
        timestamp: Long,
        initial: SessionZygote,
    ): Envelope<SessionPayload>? =
        when (state) {
            AppState.FOREGROUND -> snapshotSession(initial)
            AppState.BACKGROUND -> snapshotBackgroundActivity(initial)
        }

    override fun startSessionWithManual(timestamp: Long): SessionZygote {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.MANUAL,
                timestamp,
                AppState.FOREGROUND
            )
        )
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionZygote): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = true,
            )
        )
    }

    override fun createEmptyLogEnvelope(): Envelope<LogPayload> {
        return logEnvelopeSource.getEmptySingleLogEnvelope()
    }

    private fun startSessionWithState(timestamp: Long, coldStart: Boolean): SessionZygote {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams(
                coldStart,
                LifeEventType.STATE,
                timestamp,
                AppState.FOREGROUND
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
                AppState.BACKGROUND
            )
        )
    }

    private fun endSessionWithState(initial: SessionZygote): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = isBackgroundActivityEnabled(),
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
                continueMonitoring = true,
            )
        )
    }

    private fun endSessionWithCrash(
        initial: SessionZygote,
        crashId: String,
    ): Envelope<SessionPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger,
                continueMonitoring = false,
                crashId = crashId
            )
        )
    }

    private fun endBackgroundActivityWithCrash(
        initial: SessionZygote,
        crashId: String,
    ): Envelope<SessionPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger,
                continueMonitoring = false,
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
                continueMonitoring = true
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
                continueMonitoring = true
            )
        )
    }

    private fun isBackgroundActivityEnabled(): Boolean = configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()
}
