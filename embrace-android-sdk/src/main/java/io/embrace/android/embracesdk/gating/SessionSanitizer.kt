package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_USER_TERMINATION
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
        return session.copy(
            terminationTime = terminationTime
        )
    }

    private fun shouldSendUserTerminations() =
        enabledComponents.contains(SESSION_USER_TERMINATION)
}
