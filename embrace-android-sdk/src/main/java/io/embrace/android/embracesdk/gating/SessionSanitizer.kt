package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOGS_INFO
import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOGS_WARN
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_MOMENTS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_ORIENTATIONS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_PROPERTIES
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_USER_TERMINATION
import io.embrace.android.embracesdk.gating.SessionGatingKeys.STARTUP_MOMENT
import io.embrace.android.embracesdk.payload.Session

internal class SessionSanitizer(
    private val session: Session,
    private val enabledComponents: Set<String>
) : Sanitizable<Session> {

    @Suppress("ComplexMethod")
    override fun sanitize(): Session {
        val properties = when {
            !shouldSendSessionProperties() -> null
            else -> session.properties
        }
        val orientations = when {
            !shouldSendTrackedOrientations() -> null
            else -> session.orientations
        }
        val terminationTime = when {
            !shouldSendUserTerminations() -> null
            else -> session.terminationTime
        }
        val receivedTermination = when {
            !shouldSendUserTerminations() -> null
            else -> session.isReceivedTermination
        }
        val infoLogIds = when {
            !shouldSendInfoLog() -> null
            else -> session.infoLogIds
        }
        val infoLogsAttemptedToSend = when {
            !shouldSendInfoLog() -> null
            else -> session.infoLogsAttemptedToSend
        }
        val warnLogIds = when {
            !shouldSendWarnLog() -> null
            else -> session.warningLogIds
        }
        val warnLogsAttemptedToSend = when {
            !shouldSendWarnLog() -> null
            else -> session.warnLogsAttemptedToSend
        }
        val eventIds = when {
            !shouldSendMoment() -> null
            else -> session.eventIds
        }
        val startupDuration = when {
            !shouldSendStartupMoment() -> null
            else -> session.startupDuration
        }
        val startupThreshold = when {
            !shouldSendStartupMoment() -> null
            else -> session.startupThreshold
        }
        return session.copy(
            betaFeatures = null, // always disable beta features if the gating config has been enabled
            properties = properties,
            orientations = orientations,
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            infoLogIds = infoLogIds,
            infoLogsAttemptedToSend = infoLogsAttemptedToSend,
            warningLogIds = warnLogIds,
            warnLogsAttemptedToSend = warnLogsAttemptedToSend,
            eventIds = eventIds,
            startupDuration = startupDuration,
            startupThreshold = startupThreshold
        )
    }

    private fun shouldSendSessionProperties() =
        enabledComponents.contains(SESSION_PROPERTIES)

    private fun shouldSendTrackedOrientations() =
        enabledComponents.contains(SESSION_ORIENTATIONS)

    private fun shouldSendUserTerminations() =
        enabledComponents.contains(SESSION_USER_TERMINATION)

    private fun shouldSendMoment() =
        enabledComponents.contains(SESSION_MOMENTS)

    private fun shouldSendInfoLog() =
        enabledComponents.contains(LOGS_INFO)

    private fun shouldSendWarnLog() =
        enabledComponents.contains(LOGS_WARN)

    private fun shouldSendStartupMoment() =
        enabledComponents.contains(STARTUP_MOMENT)
}
