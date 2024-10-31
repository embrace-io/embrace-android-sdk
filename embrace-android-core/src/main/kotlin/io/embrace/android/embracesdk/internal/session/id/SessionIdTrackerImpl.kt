package io.embrace.android.embracesdk.internal.session.id

import android.app.ActivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.concurrent.CopyOnWriteArraySet

internal class SessionIdTrackerImpl(
    private val activityManager: ActivityManager?,
    private val logger: EmbLogger,
) : SessionIdTracker {

    private val listeners = CopyOnWriteArraySet<(String?) -> Unit>()

    @Volatile
    private var activeSession: SessionData? = null
        set(value) {
            field = value
            listeners.forEach { it(value?.id) }
        }

    override fun addListener(listener: (String?) -> Unit) {
        listeners.add(listener)
        listener(activeSession?.id)
    }

    override fun getActiveSession(): SessionData? = activeSession

    override fun setActiveSession(sessionId: String?, isSession: Boolean) {
        activeSession = sessionId?.run { SessionData(sessionId, isSession) }

        if (isSession) {
            setSessionIdToProcessStateSummary(sessionId)
        }
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
                    logger.logError("Couldn't set Process State Summary", e)
                    logger.trackInternalError(InternalErrorType.PROCESS_STATE_SUMMARY_FAIL, e)
                }
            }
        }
    }
}
