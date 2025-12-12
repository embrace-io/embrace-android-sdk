package io.embrace.android.embracesdk.internal.session.id

import android.app.ActivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.session.SessionZygote
import java.util.concurrent.CopyOnWriteArraySet

internal class SessionTrackerImpl(
    private val activityManager: ActivityManager?,
    private val logger: EmbLogger,
) : SessionTracker {

    private val listeners = CopyOnWriteArraySet<SessionChangeListener>()

    @Volatile
    private var activeSession: SessionZygote? = null
        set(value) {
            field = value
            try {
                listeners.forEach(SessionChangeListener::onPostSessionChange)
            } catch (ignored: Throwable) {
            }
        }

    override fun addListener(listener: SessionChangeListener) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionZygote? = activeSession

    override fun newActiveSession(
        endSessionCallback: SessionZygote.() -> Unit,
        startSessionCallback: () -> SessionZygote?,
        postTransitionAppState: AppState,
    ): SessionZygote? {
        activeSession?.endSessionCallback()
        activeSession = startSessionCallback()

        if (postTransitionAppState == AppState.FOREGROUND) {
            setSessionIdToProcessStateSummary(activeSession?.sessionId)
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
