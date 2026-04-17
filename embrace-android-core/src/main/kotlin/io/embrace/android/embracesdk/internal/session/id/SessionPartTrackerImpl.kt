package io.embrace.android.embracesdk.internal.session.id

import android.app.ActivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import java.util.concurrent.CopyOnWriteArraySet

internal class SessionPartTrackerImpl(
    private val activityManager: ActivityManager?,
    private val logger: InternalLogger,
) : SessionPartTracker {

    private val sessionChangeListeners = CopyOnWriteArraySet<SessionPartChangeListener>()
    private val sessionEndListeners = CopyOnWriteArraySet<SessionPartEndListener>()

    @Volatile
    private var activeSession: SessionPartToken? = null

    override fun addSessionPartChangeListener(listener: SessionPartChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun addSessionPartEndListener(listener: SessionPartEndListener) {
        sessionEndListeners.add(listener)
    }

    override fun getActiveSessionPart(): SessionPartToken? = activeSession

    override fun newActiveSessionPart(
        endSessionPartCallback: SessionPartToken.() -> Unit,
        startSessionPartCallback: () -> SessionPartToken?,
        postTransitionAppState: AppState,
    ): SessionPartToken? {
        activeSession?.let { endingSession ->
            runCatching {
                sessionEndListeners.forEach(SessionPartEndListener::onPreSessionEnd)
            }
            endingSession.endSessionPartCallback()
        }

        activeSession = startSessionPartCallback()
        runCatching {
            sessionChangeListeners.forEach(SessionPartChangeListener::onPostSessionChange)
        }

        if (postTransitionAppState == AppState.FOREGROUND) {
            setSessionIdToProcessStateSummary(activeSession?.sessionPartId)
        }

        return activeSession
    }

    /**
     * On android 11+, we use ActivityManager#setProcessStateSummary to store sessionId
     * Then, this information will be included in the record of ApplicationExitInfo on the death of the current calling process
     *
     * @param sessionId current session id
     */
    private fun setSessionIdToProcessStateSummary(sessionId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (sessionId != null) {
                try {
                    activityManager?.setProcessStateSummary(sessionId.toByteArray())
                } catch (e: Throwable) {
                    logger.trackInternalError(InternalErrorType.PROCESS_STATE_SUMMARY_FAIL, e)
                }
            }
        }
    }
}
