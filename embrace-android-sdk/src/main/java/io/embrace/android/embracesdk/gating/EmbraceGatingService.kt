package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.gating.v2.EnvelopeSanitizerFacade
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage

/**
 * Receives the local and remote config to build the Gating config and define the amount of
 * data (components) that the SDK sends to the backend as part of sessions or event messages.
 * The service is listening to the remote config changes and determines if the gating config should
 * be updated.
 * Event service, Session service and Crash service check if should gate data based on the gating config.
 * Also defines if a full session data should be sent under certain conditions based on configurations.
 */
internal class EmbraceGatingService(
    private val configService: ConfigService
) : GatingService {

    /**
     * This class manages the configuration of the Gating feature. The Gating configuration consists of two lists:
     * 'components' and a secondary list for special events.
     *
     * sessionComponents: This list functions as a whitelist for determining the information to include
     * in the next session message. Its state impacts the gating feature as follows:
     *
     * - If the 'components' list is null, the gating feature is disabled and all data can be included
     * in the next session message.
     * - If the 'components' list is empty, the gating feature is enabled but blocks all components from
     * being included in the next session message.
     * - If the 'components' list contains specific fields, only those fields should be included in the
     * next session message.
     *
     *  fullSessionEvents list: If this list contains entries such as "CRASH" or "EVENT",
     *  the SDK should include the full payload for sessions that incorporate a crash or event,
     *  regardless of the 'components' list status.
     *
     */
    override fun gateSessionMessage(sessionMessage: SessionMessage): SessionMessage {
        val components = configService.sessionBehavior.getSessionComponents()
        if (components != null && configService.sessionBehavior.isGatingFeatureEnabled()) {
            InternalStaticEmbraceLogger.logDebug("Session gating feature enabled. Attempting to sanitize the session message")

            // check if the session has error logs IDs. If so, send the full session payload.
            if (sessionMessage.session.errorLogIds?.isNotEmpty() == true &&
                configService.sessionBehavior.shouldSendFullForErrorLog()
            ) {
                logDeveloper(
                    "EmbraceGatingService",
                    "Error logs detected - Sending full session payload"
                )
                return sessionMessage
            }

            // check if the session has a crash report id. If so, send the full session payload.
            if (sessionMessage.session.crashReportId != null) {
                logDeveloper(
                    "EmbraceGatingService",
                    "Crash detected - Sending full session payload"
                )
                return sessionMessage
            }

            return SessionSanitizerFacade(sessionMessage, components).getSanitizedMessage()
        }

        logDeveloper("EmbraceGatingService", "Gating feature disabled")
        return sessionMessage
    }

    override fun gateSessionEnvelope(
        sessionMessage: SessionMessage,
        envelope: Envelope<SessionPayload>
    ): Envelope<SessionPayload> {
        val components = configService.sessionBehavior.getSessionComponents()
        if (components != null && configService.sessionBehavior.isGatingFeatureEnabled()) {
            // check if the session has error logs IDs. If so, send the full session payload.
            if (sessionMessage.session.errorLogIds?.isNotEmpty() == true &&
                configService.sessionBehavior.shouldSendFullForErrorLog()
            ) {
                return envelope
            }

            // check if the session has a crash report id. If so, send the full session payload.
            if (sessionMessage.session.crashReportId != null) {
                return envelope
            }
            return EnvelopeSanitizerFacade(envelope, components).sanitize()
        }
        return envelope
    }

    override fun gateEventMessage(eventMessage: EventMessage): EventMessage {
        val components = configService.sessionBehavior.getSessionComponents()
        if (components != null && configService.sessionBehavior.isGatingFeatureEnabled()) {
            InternalStaticEmbraceLogger.logDebug("Session gating feature enabled. Attempting to sanitize the event message")

            if (configService.sessionBehavior.shouldSendFullMessage(eventMessage)) {
                logDeveloper(
                    "EmbraceGatingService",
                    "Crash or error detected - Sending full session payload"
                )
                return eventMessage
            }

            return EventSanitizerFacade(eventMessage, components).getSanitizedMessage()
        }

        logDeveloper("EmbraceGatingService", "Gating feature disabled")
        return eventMessage
    }
}
