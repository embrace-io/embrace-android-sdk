package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.gating.v2.EnvelopeSanitizerFacade
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * Receives the local and remote config to build the Gating config and define the amount of
 * data (components) that the SDK sends to the backend as part of sessions or event messages.
 * The service is listening to the remote config changes and determines if the gating config should
 * be updated.
 * Event service, Session service and Crash service check if should gate data based on the gating config.
 * Also defines if a full session data should be sent under certain conditions based on configurations.
 */
internal class EmbraceGatingService(
    private val configService: ConfigService,
    private val logService: LogService,
) : GatingService {

    override fun gateSessionEnvelope(
        hasCrash: Boolean,
        envelope: Envelope<SessionPayload>,
    ): Envelope<SessionPayload> {
        val components = configService.sessionBehavior.getSessionComponents()
        if (components != null && configService.sessionBehavior.isGatingFeatureEnabled()) {
            // check if the session has error logs IDs. If so, send the full session payload.
            if (hasErrorLogs()) {
                return envelope
            }

            // check if the session has a crash report id. If so, send the full session payload.
            if (hasCrash) {
                return envelope
            }
            return EnvelopeSanitizerFacade(envelope, components).sanitize()
        }
        return envelope
    }

    private fun hasErrorLogs(): Boolean {
        return logService.getErrorLogsCount() > 0 &&
            configService.sessionBehavior.shouldSendFullForErrorLog()
    }
}
