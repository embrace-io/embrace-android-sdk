package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

internal class PayloadFactoryImpl(
    private val payloadMessageCollator: PayloadMessageCollator,
    private val logEnvelopeSource: LogEnvelopeSource,
    private val configService: ConfigService,
    private val logger: InternalLogger,
) : PayloadFactory {

    override fun startPayloadWithState(state: AppState, timestamp: Long, coldStart: Boolean, partNumber: Int): SessionPartToken? =
        when (state) {
            AppState.FOREGROUND -> startSessionWithState(timestamp, coldStart, partNumber)
            AppState.BACKGROUND -> startBackgroundActivityWithState(timestamp, coldStart, partNumber)
        }

    override fun endPayloadWithState(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload>? =
        when (state) {
            AppState.FOREGROUND -> endSessionWithState(initial)
            AppState.BACKGROUND -> endBackgroundActivityWithState(initial)
        }

    override fun endPayloadWithCrash(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
        crashId: String,
    ): Envelope<SessionPartPayload>? = when (state) {
        AppState.FOREGROUND -> endSessionWithCrash(initial, crashId)
        AppState.BACKGROUND -> endBackgroundActivityWithCrash(initial, crashId)
    }

    override fun snapshotPayload(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload>? =
        when (state) {
            AppState.FOREGROUND -> snapshotSession(initial)
            AppState.BACKGROUND -> snapshotBackgroundActivity(initial)
        }

    override fun startSessionWithManual(state: AppState, timestamp: Long, partNumber: Int): SessionPartToken? {
        if (state == AppState.BACKGROUND && !isBackgroundActivityEnabled()) {
            return null
        }
        val startType = when (state) {
            AppState.FOREGROUND -> LifeEventType.MANUAL
            AppState.BACKGROUND -> LifeEventType.BKGND_MANUAL
        }
        return payloadMessageCollator.buildInitialPart(
            InitialEnvelopeParams(
                coldStart = false,
                startType = startType,
                startTime = timestamp,
                appState = state,
                partNumber = partNumber,
            )
        )
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionPartToken): Envelope<SessionPartPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = true,
            )
        )
    }

    override fun createEmptyLogEnvelope(): Envelope<LogPayload> {
        return logEnvelopeSource.getEmptySingleLogEnvelope()
    }

    private fun startSessionWithState(timestamp: Long, coldStart: Boolean, partNumber: Int): SessionPartToken {
        return payloadMessageCollator.buildInitialPart(
            InitialEnvelopeParams(
                coldStart,
                LifeEventType.STATE,
                timestamp,
                AppState.FOREGROUND,
                partNumber,
            )
        )
    }

    private fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean, partNumber: Int): SessionPartToken? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }

        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val time = when {
            coldStart -> timestamp
            else -> timestamp + 1
        }
        return payloadMessageCollator.buildInitialPart(
            InitialEnvelopeParams(
                coldStart = coldStart,
                startType = LifeEventType.BKGND_STATE,
                startTime = time,
                AppState.BACKGROUND,
                partNumber,
            )
        )
    }

    private fun endSessionWithState(initial: SessionPartToken): Envelope<SessionPartPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = isBackgroundActivityEnabled(),
            )
        )
    }

    private fun endBackgroundActivityWithState(initial: SessionPartToken): Envelope<SessionPartPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }

        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = true,
            )
        )
    }

    private fun endSessionWithCrash(
        initial: SessionPartToken,
        crashId: String,
    ): Envelope<SessionPartPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.JVM_CRASH,
                logger = logger,
                continueMonitoring = false,
                crashId = crashId
            )
        )
    }

    private fun endBackgroundActivityWithCrash(
        initial: SessionPartToken,
        crashId: String,
    ): Envelope<SessionPartPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.JVM_CRASH,
                logger = logger,
                continueMonitoring = false,
                crashId = crashId
            )
        )
    }

    /**
     * Called when the session is persisted every 2s to cache its state.
     */
    private fun snapshotSession(initial: SessionPartToken): Envelope<SessionPartPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.PERIODIC_CACHE,
                logger = logger,
                continueMonitoring = true
            )
        )
    }

    private fun snapshotBackgroundActivity(initial: SessionPartToken): Envelope<SessionPartPayload>? {
        if (!isBackgroundActivityEnabled()) {
            return null
        }
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.PERIODIC_CACHE,
                logger = logger,
                continueMonitoring = true
            )
        )
    }

    private fun isBackgroundActivityEnabled(): Boolean = configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()
}
