package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
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

    override fun startPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        coldStart: Boolean,
        userSessionPartIndex: () -> Int,
        sessionPartNumber: () -> Int,
    ): SessionPartToken? =
        when (state) {
            ProcessState.FOREGROUND -> startSessionWithState(timestamp, coldStart, userSessionPartIndex, sessionPartNumber)
            ProcessState.BACKGROUND -> startBackgroundActivityWithState(timestamp, coldStart, userSessionPartIndex, sessionPartNumber)
        }

    override fun endPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload>? =
        when (state) {
            ProcessState.FOREGROUND -> endSessionWithState(initial)
            ProcessState.BACKGROUND -> endBackgroundActivityWithState(initial)
        }

    override fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionPartToken,
        crashId: String,
    ): Envelope<SessionPartPayload>? = when (state) {
        ProcessState.FOREGROUND -> endSessionWithCrash(initial, crashId)
        ProcessState.BACKGROUND -> endBackgroundActivityWithCrash(initial, crashId)
    }

    override fun snapshotPayload(
        state: ProcessState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload>? =
        when (state) {
            ProcessState.FOREGROUND -> snapshotSession(initial)
            ProcessState.BACKGROUND -> snapshotBackgroundActivity(initial)
        }

    override fun startSessionWithManual(
        state: ProcessState,
        timestamp: Long,
        userSessionPartIndex: () -> Int,
        sessionPartNumber: () -> Int,
    ): SessionPartToken? {
        if (state == ProcessState.BACKGROUND && !isBackgroundActivityEnabled()) {
            return null
        }
        val startType = when (state) {
            ProcessState.FOREGROUND -> LifeEventType.MANUAL
            ProcessState.BACKGROUND -> LifeEventType.BKGND_MANUAL
        }
        return payloadMessageCollator.buildInitialPart(
            InitialEnvelopeParams(
                coldStart = false,
                startType = startType,
                startTime = timestamp,
                processState = state,
                userSessionPartIndex = userSessionPartIndex(),
                sessionPartNumber = sessionPartNumber(),
            ),
        )
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionPartToken): Envelope<SessionPartPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = true,
            ),
        )
    }

    override fun createEmptyLogEnvelope(): Envelope<LogPayload> {
        return logEnvelopeSource.getEmptySingleLogEnvelope()
    }

    private fun startSessionWithState(
        timestamp: Long,
        coldStart: Boolean,
        userSessionPartIndex: () -> Int,
        sessionPartNumber: () -> Int,
    ): SessionPartToken {
        return payloadMessageCollator.buildInitialPart(
            InitialEnvelopeParams(
                coldStart = coldStart,
                startType = LifeEventType.STATE,
                startTime = timestamp,
                processState = ProcessState.FOREGROUND,
                userSessionPartIndex = userSessionPartIndex(),
                sessionPartNumber = sessionPartNumber(),
            ),
        )
    }

    private fun startBackgroundActivityWithState(
        timestamp: Long,
        coldStart: Boolean,
        userSessionPartIndex: () -> Int,
        sessionPartNumber: () -> Int,
    ): SessionPartToken? {
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
                processState = ProcessState.BACKGROUND,
                userSessionPartIndex = userSessionPartIndex(),
                sessionPartNumber = sessionPartNumber(),
            ),
        )
    }

    private fun endSessionWithState(initial: SessionPartToken): Envelope<SessionPartPayload> {
        return payloadMessageCollator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = initial,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = logger,
                continueMonitoring = isBackgroundActivityEnabled(),
            ),
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
            ),
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
                crashId = crashId,
            ),
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
                crashId = crashId,
            ),
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
                continueMonitoring = true,
            ),
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
                continueMonitoring = true,
            ),
        )
    }

    private fun isBackgroundActivityEnabled(): Boolean = configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()
}
