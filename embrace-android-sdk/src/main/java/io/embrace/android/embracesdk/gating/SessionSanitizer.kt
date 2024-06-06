package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOGS_INFO
import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOGS_WARN
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_MOMENTS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_USER_TERMINATION
import io.embrace.android.embracesdk.gating.SessionGatingKeys.STARTUP_MOMENT
import io.embrace.android.embracesdk.payload.Session

internal class SessionSanitizer(
    private val session: Session,
    private val enabledComponents: Set<String>
) : Sanitizable<Session> {

    @Suppress("ComplexMethod")
    override fun sanitize(): Session {
        val terminationTime = when {
            !shouldSendUserTerminations() -> null
            else -> session.terminationTime
        }
        val infoLogIds = when {
            !shouldSendInfoLog() -> null
            else -> session.infoLogIds
        }
        val warnLogIds = when {
            !shouldSendWarnLog() -> null
            else -> session.warningLogIds
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
            terminationTime = terminationTime,
            infoLogIds = infoLogIds,
            warningLogIds = warnLogIds,
            eventIds = eventIds,
            startupDuration = startupDuration,
            startupThreshold = startupThreshold
        )
    }

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
