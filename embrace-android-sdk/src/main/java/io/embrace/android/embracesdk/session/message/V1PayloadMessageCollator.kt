package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class V1PayloadMessageCollator(
    private val gatingService: GatingService,
    private val eventService: EventService,
    private val logMessageService: LogMessageService,
    private val preferencesService: PreferencesService,
    private val currentSessionSpan: CurrentSessionSpan,
    private val sessionPropertiesService: SessionPropertiesService,
    private val startupService: StartupService,
    private val logger: EmbLogger,
) : PayloadMessageCollator {

    /**
     * Builds a new session object. This should not be sent to the backend but is used
     * to populate essential session information (such as ID), etc
     */
    override fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
        Session(
            sessionId = currentSessionSpan.getSessionId(),
            startTime = startTime,
            isColdStart = coldStart,
            appState = appState,
            startType = startType,
            number = getSessionNumber(preferencesService),
            properties = getProperties(sessionPropertiesService),
        )
    }

    /**
     * Builds a fully populated session message. This can be sent to the backend (or stored
     * on disk).
     */
    override fun buildFinalSessionMessage(
        params: FinalEnvelopeParams.SessionParams
    ): SessionMessage = with(params) {
        val base = buildFinalBackgroundActivity(params)
        val startupInfo = getStartupEventInfo(eventService)

        val endSession = base.copy(
            isEndedCleanly = endType.endedCleanly,
            networkLogIds = captureDataSafely(logger) {
                logMessageService.findNetworkLogIds(
                    initial.startTime,
                    endTime
                )
            },
            properties = captureDataSafely(logger, sessionPropertiesService::getProperties),
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = startupService.getSdkStartupDuration(initial.isColdStart),
            startupDuration = startupInfo?.duration,
            startupThreshold = startupInfo?.threshold
        )
        val envelope = buildWrapperEnvelope(endSession)
        return gatingService.gateSessionMessage(envelope)
    }

    /**
     * Create the background session message with the current state of the background activity.
     */
    override fun buildFinalBackgroundActivityMessage(
        params: FinalEnvelopeParams.BackgroundActivityParams
    ): SessionMessage {
        val msg = buildFinalBackgroundActivity(params)
        val envelope = buildWrapperEnvelope(msg)
        return gatingService.gateSessionMessage(envelope)
    }

    /**
     * Creates a background activity stop message.
     */
    private fun buildFinalBackgroundActivity(
        params: FinalEnvelopeParams
    ): Session = with(params) {
        val startTime = initial.startTime
        return initial.copy(
            endTime = endTime,
            eventIds = captureDataSafely(logger) {
                eventService.findEventIdsForSession()
            },
            infoLogIds = captureDataSafely(logger) { logMessageService.findInfoLogIds(startTime, endTime) },
            warningLogIds = captureDataSafely(logger) {
                logMessageService.findWarningLogIds(
                    startTime,
                    endTime
                )
            },
            errorLogIds = captureDataSafely(logger) {
                logMessageService.findErrorLogIds(
                    startTime,
                    endTime
                )
            },
            lastHeartbeatTime = endTime,
            endType = lifeEventType,
            crashReportId = crashId
        )
    }

    private fun buildWrapperEnvelope(finalPayload: Session) = SessionMessage(session = finalPayload)
}
